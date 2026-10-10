# Multi-device sync

The same ledger on several devices through Firebase (Auth + Firestore), end-to-end encrypted:
each record is a Firestore document `users/{uid}/records/{table}_{syncId}` whose payload is
encrypted on the device with a key only the user's passphrase gives.

| Phase | What | State |
|---|---|---|
| 1 | Local groundwork: record identity, change capture, outbox | done (database version 71) |
| 2 | Firebase sign-in, wire protocol, push and pull, the sync page (Backup & sync → Firebase sync) | done (database version 72) |

Sync is in the **standard flavor only**. The F-Droid flavor links no Firebase, Credential
Manager or googleid code; its Firebase sync entry and page say sync is unavailable (see [Code](#code)).

Contents: the local groundwork ([Synced tables](#synced-tables) to [Backups](#backups)), the
[Wire protocol](#wire-protocol) another client (iOS) must follow, and the [Engine](#engine) this
app runs.

## Synced tables

`accounts`, `account_currencies`, `account_balances`, `transactions`, `categories`,
`subcategories`, `cards`, `budgets`, `budget_category_limits`, `subscriptions`,
`lend_borrow_persons`, `lend_borrow_transactions`, `quick_templates`
(`SyncTriggers.TABLES`).

`exchange_rates` is not synced, because the rates are fetched, not entered.

## Columns

Each synced table has two extra columns:

| Column | Entity field | Meaning |
|---|---|---|
| `sync_id TEXT NOT NULL DEFAULT ''` | `syncId` | The record's identity on every device: 32 lowercase hex digits |
| `sync_updated_at INTEGER NOT NULL DEFAULT 0` | `syncUpdatedAt` | Epoch millis of the last local change |

Each table has a plain index `index_<table>_sync_id` on `sync_id`.

Two choices differ from the first sketch:

- **`sync_updated_at`, not `updated_at`.** Most of these tables already have an `updated_at`
  column, a `LocalDateTime` stored as text.
- **The index is not unique.** Uniqueness is kept by the triggers. App code makes new rows by
  copying saved entities (for example `latest.copy(id = 0, …)` in a balance calibration), and
  the copy carries the original's `sync_id`. With a unique index, such an insert would fail
  under ABORT and be dropped under IGNORE. Under REPLACE, the most dangerous case, it would
  delete the original row.

Migration 70→71 adds the columns and gives every existing row a new id and the current time. It
also creates the tables below and the triggers. Existing rows are not queued: the first sync must
upload everything anyway.

## Tables

- **`sync_outbox(table_name, sync_id, op, queued_at, replaced_by)`**: primary key
  `(table_name, sync_id)`. There is one row per changed record, and the latest change replaces
  earlier ones. `op` is `UPSERT` or `DELETE`. Entity: `SyncOutboxEntity`; DAO: `SyncDao`.
  - `pending()` reads the queue.
  - `markSent(entry)` removes an entry only while it is unchanged (same op and `queued_at`). A
    record that changed again while its upload was in flight stays queued.
  - `replaced_by` (version 72) is set only on a DELETE the engine queues to resolve a duplicate
    (see [Duplicates](#duplicates)); the triggers never set it.
- **`sync_control(id = 1, applying_remote, capturing)`**: a single row (`SyncControlEntity`).
  - `applying_remote = 1` pauses capture: see below.
  - `capturing` is set by the triggers while they update their own row, so that update does not
    capture itself.
- **`sync_inbox`** (version 72, `SyncInboxEntity`): remote records that cannot be applied yet,
  kept as received (payload still encrypted), with `waiting_for`: the document id of a missing
  parent, or `version` for a record of a newer protocol version.

## Triggers

`SyncTriggers` defines three triggers per table, named `sync_v1_<table>_insert|update|delete`.

| Trigger | What it does |
|---|---|
| After insert | Gives the row a new `sync_id` if it has none, or if another row already holds that id (a copy). Sets `sync_updated_at = max(now, incoming value)` and queues `UPSERT`. |
| After update | Keeps the old `sync_id`: an update that blanks it (an entity built afresh) or changes it gets the old id back, so a record's identity never changes. Sets `sync_updated_at = max(now, old + 1)` and queues `UPSERT`. |
| After delete | Queues `DELETE` with the old `sync_id`. Foreign-key cascades fire it too, for example a budget's limits or an account's currencies. |

Notes on how the triggers are written:

- **No `OR REPLACE` / `OR IGNORE` in trigger bodies.** In SQLite, the conflict policy of the
  statement that fired a trigger overrides the policy of statements inside it. An outbox entry is
  therefore replaced by a delete followed by an insert.
- **`recursive_triggers`.** `SyncTriggers.Callback.onOpen` turns this pragma on for the
  connection that writes. Without it, the rows that an `INSERT OR REPLACE` deletes would not
  queue their `DELETE`.
- **Where the triggers are created.** They are not part of Room's schema. `SyncTriggers.install`
  creates them with `CREATE TRIGGER IF NOT EXISTS`, and is called from:
  - migration 70→71;
  - `SyncTriggers.Callback.onCreate` (new installs, in-memory test databases);
  - `SyncTriggers.Callback.onOpen` (every open).
- **What `onOpen` also does.** It drops sync triggers of other versions and clears flags that a
  crash may have left set. It also gives an id to any row written while the triggers were
  missing.
- **Every builder needs the callback.** Every database builder, tests included, adds
  `SyncTriggers.Callback` before any callback that writes. `DatabaseModule` adds it before
  `DatabaseCallback`, so the seeded categories are captured.
- **Changing a trigger.** To change a trigger body, bump `SyncTriggers.VERSION`.

## Backups

Gson carries `syncId` / `syncUpdatedAt` in `backup.json` without extra code.

- **Old backups** have no such fields. `SyncIdDefaultsFactory` (Gson) and the `sanitize()`
  functions read a missing id as empty, and the insert trigger then gives the record a new one.
- **REPLACE_ALL** keeps the backup's ids.
  - Clearing the tables queues a `DELETE` for every old record.
  - Re-inserting queues an `UPSERT` for every restored one; a restored record with the same id
    replaces its `DELETE`.
  - The result is the restored state, ready to upload. Every restored record is stamped with the
    current time, so it wins against older remote copies.
- **MERGE** keeps the existing merge rules.
  - A transaction is also recognized by its `sync_id`, so an edit on another device (a new hash)
    updates the same record instead of adding a copy.
  - An updated record keeps its local `sync_id`.
  - A record the rules add whose `sync_id` is already taken locally gets a new id from the
    trigger, so a merge never fails on an id.
  - Accounts are still matched by name and last 4, because balance rows name their account.

## Wire protocol

Version 1. Everything a client needs to read and write the same account as this app. Code:
`SyncSchema` (columns), `SyncCrypto` (encryption), `FirestoreRemoteStore` (documents).

### Account and paths

- Firebase project `cashiro-7a585`, Firestore (region asia-east2). Sign-in: Google, through
  Firebase Auth; `uid` is the Firebase user id.
- Security rules: `match /users/{uid}/{document=**} { allow read, write: if request.auth != null
  && request.auth.uid == uid; }`. Nothing outside `users/{uid}` is used.
- `users/{uid}/meta/crypto`: the encryption parameters, one document.
- `users/{uid}/records/{table}_{syncId}`: one document per record, for example
  `transactions_00112233445566778899aabbccddeeff`. `table` is one of the 13 table names below
  (they contain underscores; the sync id is always the last 32 characters).

### Record documents

| Field | Firestore type | Meaning |
|---|---|---|
| `table` | string | Table name, as in the document id |
| `syncId` | string | 32 lowercase hex digits, as in the document id |
| `updatedAt` | timestamp | `FieldValue.serverTimestamp()` on every write. Orders changes and decides conflicts |
| `deleted` | boolean | `true`: a tombstone. Deletes keep the document |
| `payload` | string | Base64 of the encrypted row (see [Encryption](#encryption)). Absent on a tombstone |
| `deviceId` | string | The writing installation: an opaque id, stable per install (32 hex digits here) |
| `v` | integer | Protocol version, `1`. A reader holds (does not apply) a record with a higher `v` |
| `replacedBy` | string | Only on a tombstone that resolved a duplicate: the sync id (same table) of the record that replaces this one. See [Duplicates](#duplicates) |

Rules for writers:

- Write a document whole (`set` without merge), never patch it: a tombstone then carries no
  payload, and a record that comes back after a delete is a plain write with `deleted: false`.
- New sync ids are random: 16 random bytes as 32 lowercase hex digits (a UUID v4 without
  dashes is fine). A record keeps its sync id for life.
- Write a parent before (or in the same batch as) the records that refer to it. Readers cope
  with any order, but hold a child until its parent arrives.
- Base64 is the standard alphabet with `=` padding, no line breaks (RFC 4648 §4).

### Payload

The plaintext is UTF-8 JSON: one object, keyed by column name, holding the row as the database
stores it. Keys may come in any order.

- `id` (the local autoincrement id) and `sync_id` are never in it.
- A reference to another record is the referenced record's **sync id** under the column's own
  name (`"account_id": "9f2c…"`), or `null`. A reference to a record that no longer exists is
  sent as `null`.
- Columns marked "not sent" below are device-specific (file paths, Android resource ids). They
  are never in the payload; a receiver keeps its own value, or the column default on insert.
- Readers ignore keys they do not know (a newer schema) and leave a column that a payload lacks
  unchanged (its default on insert).

| Type | JSON | Example |
|---|---|---|
| string | string | `"招商银行"` |
| integer | number | `5` |
| boolean | `true` / `false` (stored as 0 / 1; readers also accept 0 / 1) | `true` |
| decimal string | string, plain decimal: optional `-`, digits, optional `.` and digits; no grouping, no exponent (readers accept an exponent) | `"1085.20"` |
| date-time string | string, local wall-clock time with no zone, as Java's `LocalDateTime` writes it: `yyyy-MM-dd'T'HH:mm:ss` with an optional fraction of 1–9 digits. Rows written by SQLite (built-in categories, old data) use a space for the `T`, and very old ones may lack seconds: readers accept `yyyy-MM-dd[T| ]HH:mm[:ss[.f…]]`. Writers use the `T` form | `"2026-09-03T12:30:15"` |
| date string | string, `yyyy-MM-dd` | `"2026-10-01"` |
| sync id of `t` | string (32 hex) or `null` | `"00112233445566778899aabbccddeeff"` |

`sync_updated_at` (integer, every table) is the epoch milliseconds of the record's last change
on the device that made it. It is copied as is; ordering uses the server's `updatedAt`.

Values that are names, not references: `transactions.category` / `subcategory`,
`subscriptions.category`, `quick_templates.category`, `budget_category_limits.category_name`
and `lend_borrow_transactions.category_name` hold category **names**. `bank_name` with
`account_number` (transactions) or `account_last4` (balances, cards, templates) hold an
account's `name` and `last4`, which much of the app still uses to find an account: a writer
sets them consistently with `account_id`. `budgets.account_ids` is a comma-separated list of
`name:last4`.

Enumerations (stored as their names):

- `transactions.transaction_type`, `quick_templates.transaction_type`: `INCOME`, `EXPENSE`,
  `CREDIT`, `TRANSFER`, `INVESTMENT`, `BALANCE_UPDATE`, `LENT`, `BORROWED`.
- `subscriptions.state`: `ACTIVE`, `HIDDEN`.
- `budgets.period_type`: `CUSTOM`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`;
  `budgets.track_type`: `ALL_TRANSACTIONS`; `budgets.budget_type`: `EXPENSE`, `SAVINGS`.
- `cards.card_type`: `DEBIT`, `CREDIT`.
- `lend_borrow_transactions.type`: `LENT`, `BORROWED`, `SETTLEMENT_LENT`, `SETTLEMENT_BORROWED`.
- `account_balances.source_type`: `TRANSACTION_CALCULATED`, `MANUAL`, `MANUAL_EDIT`,
  `BALANCE_CALIBRATION`, `OPENING_BALANCE`, `DELETE_REVERSAL`, `UNDO_REVERSAL` (and, in old
  data, `TRANSACTION_SMS_BALANCE`, `SMS_BALANCE`, `CARD_LINK`).

**Balances are history, not totals.** `account_balances` has one row per change of an account's
currency ("pocket"), holding the balance *after* it; an account's balance is its latest row
(by `timestamp`). Receivers copy these rows verbatim and never recompute them. A client that
adds, edits or deletes a transaction must therefore write (and upload) the balance rows itself,
the way Hootbook's `balanceMoves`, `AddTransactionUseCase` and `TransactionEditor` do, including
the rows after it that a back-dated change shifts.

### Tables and columns

Levels give the order of application: a record refers only to records of lower levels.

#### `accounts` (level 0)

| Key | Type | Null |
|---|---|---|
| `name` | string | no |
| `last4` | string | no |
| `main_currency` | string | no |
| `is_credit_card` | boolean | no |
| `is_wallet` | boolean | no |
| `credit_limit` | decimal string | yes |
| `icon_name` | string | no |
| `color` | string | no |
| `is_sample` | boolean | no |
| `created_at` | date-time string | no |
| `statement_day` | integer | yes |
| `due_day` | integer | yes |
| `sync_updated_at` | integer | no |

Not sent: `icon_res_id`.

Natural key: (`name`, `last4`).

#### `categories` (level 0)

| Key | Type | Null |
|---|---|---|
| `name` | string | no |
| `color` | string | no |
| `icon_name` | string | no |
| `description` | string | no |
| `is_system` | boolean | no |
| `is_income` | boolean | no |
| `display_order` | integer | no |
| `default_name` | string | yes |
| `default_color` | string | yes |
| `default_icon_name` | string | yes |
| `default_description` | string | yes |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `sync_updated_at` | integer | no |

Not sent: `icon_res_id`, `default_icon_res_id`.

Natural key: (`name`).

#### `budgets` (level 0)

| Key | Type | Null |
|---|---|---|
| `name` | string | no |
| `amount` | decimal string | no |
| `year` | integer | no |
| `month` | integer | no |
| `currency` | string | no |
| `is_active` | boolean | no |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `start_date` | date-time string | no |
| `end_date` | date-time string | no |
| `period_type` | string | no |
| `track_type` | string | no |
| `budget_type` | string | no |
| `account_ids` | string | no |
| `color` | string | no |
| `is_sample` | boolean | no |
| `sync_updated_at` | integer | no |

#### `lend_borrow_persons` (level 0)

| Key | Type | Null |
|---|---|---|
| `name` | string | no |
| `phone_number` | string | yes |
| `notes` | string | yes |
| `color` | string | no |
| `category` | string | yes |
| `is_archived` | boolean | no |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `sync_updated_at` | integer | no |

Not sent: `avatar`.

#### `cards` (level 0)

| Key | Type | Null |
|---|---|---|
| `card_last4` | string | no |
| `card_type` | string | no |
| `bank_name` | string | no |
| `account_last4` | string | yes |
| `nickname` | string | yes |
| `is_active` | boolean | no |
| `last_balance` | decimal string | yes |
| `last_balance_date` | date-time string | yes |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `currency` | string | no |
| `is_sample` | boolean | no |
| `sync_updated_at` | integer | no |

Natural key: (`bank_name`, `card_last4`).

#### `subscriptions` (level 0)

| Key | Type | Null |
|---|---|---|
| `merchant_name` | string | no |
| `amount` | decimal string | no |
| `next_payment_date` | date string | yes |
| `state` | string | no |
| `bank_name` | string | yes |
| `category` | string | yes |
| `subcategory` | string | yes |
| `notes` | string | yes |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `currency` | string | no |
| `billing_cycle` | string | yes |
| `last_paid_date` | date string | yes |
| `is_sample` | boolean | no |
| `sync_updated_at` | integer | no |

#### `quick_templates` (level 0)

| Key | Type | Null |
|---|---|---|
| `name` | string | no |
| `merchant_name` | string | no |
| `category` | string | no |
| `subcategory` | string | yes |
| `transaction_type` | string | no |
| `amount` | decimal string | yes |
| `prefill_amount` | boolean | no |
| `bank_name` | string | yes |
| `account_last4` | string | yes |
| `currency` | string | yes |
| `notes` | string | yes |
| `sort_order` | integer | no |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `sync_updated_at` | integer | no |

#### `account_currencies` (level 1)

| Key | Type | Null |
|---|---|---|
| `account_id` | sync id of `accounts` | no |
| `currency` | string | no |
| `credit_limit` | decimal string | yes |
| `created_at` | date-time string | no |
| `sync_updated_at` | integer | no |

Natural key: (`account_id`, `currency`).

#### `subcategories` (level 1)

| Key | Type | Null |
|---|---|---|
| `category_id` | sync id of `categories` | no |
| `name` | string | no |
| `icon_name` | string | no |
| `color` | string | no |
| `is_system` | boolean | no |
| `default_name` | string | yes |
| `default_icon_name` | string | yes |
| `default_color` | string | yes |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `sync_updated_at` | integer | no |

Not sent: `icon_res_id`, `default_icon_res_id`.

#### `budget_category_limits` (level 1)

| Key | Type | Null |
|---|---|---|
| `budget_id` | sync id of `budgets` | no |
| `category_name` | string | no |
| `limit_amount` | decimal string | no |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `sync_updated_at` | integer | no |

#### `transactions` (level 1)

| Key | Type | Null |
|---|---|---|
| `amount` | decimal string | no |
| `merchant_name` | string | no |
| `category` | string | no |
| `subcategory` | string | yes |
| `transaction_type` | string | no |
| `date_time` | date-time string | no |
| `description` | string | yes |
| `bank_name` | string | yes |
| `account_number` | string | yes |
| `balance_after` | decimal string | yes |
| `transaction_hash` | string | no |
| `is_recurring` | boolean | no |
| `is_deleted` | boolean | no |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `currency` | string | no |
| `from_account` | string | yes |
| `to_account` | string | yes |
| `to_amount` | decimal string | yes |
| `account_id` | sync id of `accounts` | yes |
| `to_account_id` | sync id of `accounts` | yes |
| `to_currency` | string | yes |
| `reference` | string | yes |
| `billing_cycle` | string | yes |
| `is_sample` | boolean | no |
| `sync_updated_at` | integer | no |

Not sent: `attachments`.

Natural key: (`transaction_hash`).

#### `account_balances` (level 2)

| Key | Type | Null |
|---|---|---|
| `icon_name` | string | no |
| `bank_name` | string | no |
| `account_last4` | string | no |
| `balance` | decimal string | no |
| `timestamp` | date-time string | no |
| `transaction_id` | sync id of `transactions` | yes |
| `credit_limit` | decimal string | yes |
| `is_credit_card` | boolean | no |
| `source_type` | string | yes |
| `created_at` | date-time string | no |
| `currency` | string | no |
| `is_wallet` | boolean | no |
| `color` | string | no |
| `is_sample` | boolean | no |
| `account_id` | sync id of `accounts` | yes |
| `sync_updated_at` | integer | no |

Not sent: `icon_res_id`.

Natural key: (`bank_name`, `account_last4`, `currency`, `timestamp`).

#### `lend_borrow_transactions` (level 2)

| Key | Type | Null |
|---|---|---|
| `person_id` | sync id of `lend_borrow_persons` | no |
| `transaction_id` | sync id of `transactions` | yes |
| `type` | string | no |
| `amount` | decimal string | no |
| `currency` | string | no |
| `title` | string | no |
| `due_date` | date-time string | yes |
| `is_settled` | boolean | no |
| `date` | date-time string | no |
| `created_at` | date-time string | no |
| `updated_at` | date-time string | no |
| `is_sample` | boolean | no |
| `account_id` | sync id of `accounts` | yes |
| `category_name` | string | yes |
| `merchant_name` | string | yes |
| `sync_updated_at` | integer | no |

Not sent: `attachments`.

### Encryption

- **Key.** PBKDF2-HMAC-SHA256 over the UTF-8 passphrase, with the account's random 16-byte salt
  and its iteration count (600000 for accounts created by this app: OWASP's 2023 figure; read
  it from `meta/crypto`, never assume it), giving 32 bytes: an AES-256 key.
- **Payload.** AES-256-GCM, a fresh random 12-byte nonce per payload, 128-bit tag, and the
  **document id** (`{table}_{syncId}`, UTF-8) as associated data, so a payload cannot be moved
  to another document. Stored as base64 of `nonce (12) || ciphertext || tag (16)`.
- **`meta/crypto`** fields: `kdf` = `"PBKDF2-HMAC-SHA256"`, `salt` (base64, 16 bytes),
  `iterations` (integer), `cipher` = `"AES-256-GCM"`, `keyCheck` (base64, as a payload),
  `v` = 1, `createdAt` (server timestamp). `keyCheck` seals the ASCII text
  `cashiro-sync-key-check-v1` with associated data `meta/crypto`.
- **First device.** Makes the salt and the key check and creates `meta/crypto` in a Firestore
  transaction only if it does not exist; when another device was first, it uses theirs.
- **Other devices.** Read `meta/crypto`, derive the key from the passphrase entered, and open
  `keyCheck`: if it fails (GCM tag mismatch), the passphrase is wrong and nothing is read.
- The passphrase is never stored or sent. The derived key is kept on the device (Android:
  encrypted preferences). A forgotten passphrase cannot be recovered: the cloud copy is
  unreadable, while each device's own data is unaffected.
- Not encrypted, so visible to the server: table names, sync ids, times, delete flags, device
  ids, `replacedBy` and the number and size of records.

**Test vector** (checked by `SyncCryptoTest`; computed independently with Python's `hashlib` and
`cryptography`):

| Input | Value |
|---|---|
| passphrase | `correct horse battery staple` |
| salt | bytes `00 01 … 0f`, base64 `AAECAwQFBgcICQoLDA0ODw==` |
| iterations | `600000` |
| key (hex) | `ef177144eec9420cbc1093d2a8b344a92bc506d0d4ec9c028dd19f8324d8c1e6` |
| associated data | `transactions_00112233445566778899aabbccddeeff` |
| nonce | bytes `10 11 … 1b` |
| plaintext | `{"amount":"12.50","currency":"CNY","merchant_name":"咖啡"}` (UTF-8) |
| payload | `EBESExQVFhcYGRobdzAcqH+KxgPpgLv5/t1ByjMUuIfzFaTOrVNiyPKbA6HH4aPLmYSET6R2zSL5lG0pJDoDfCvI94+EN6FeGLYHGlDEkuyY8f2H/asEHA==` |
| key check (nonce `20 21 … 2b`) | `ICEiIyQlJicoKSorKfauKuRIU+Dzp/RiFbSaHUnkWT6z/Gr5hCCWJAnSXRFxg9t7EsSfd4M=` |

### Reading changes

- Pull with a query on `records` ordered by `updatedAt`, then document id, starting after the
  last `(updatedAt, document id)` applied (the cursor), in pages. A write batch shares one
  server time, so the document id is part of the cursor.
- While the app is open, a snapshot listener on `records` ordered by `updatedAt` after the
  cursor signals new writes; the client then pulls. It also pulls on start and on resume.
- A device sees its own writes too; applying them again changes nothing.

### Conflicts

- **Last write to the server wins, per record.** There is no field-level merge.
- **A change not yet sent wins over an incoming one.** When a remote record arrives for a
  record with a local change still queued, the remote version is skipped: the local change is
  uploaded afterwards, so it is the later write on the server, and every device ends with it.
  This holds for deletes too (a queued delete beats a remote edit, and a queued edit brings a
  remotely deleted record back).
- Otherwise the remote record is applied: written over the local row by sync id, or deleted.

### Order and dependencies

- Within a page, deletes are applied first (children first), then upserts (parents first), so
  a record deleted and made again under the same natural key in one change (a balance row an
  edit rewrites, for example) does not clash with itself.
- A record whose reference names a record the device does not have waits in `sync_inbox` and is
  retried after every pull. After a pull the client fetches each missing parent document once:
  - a live parent is applied there and then;
  - a tombstone with `replacedBy` makes the reference point at the replacement;
  - a plain tombstone means the parent is gone: a nullable reference becomes `null`; a record
    that cannot exist without it (`subcategories`, `budget_category_limits`,
    `account_currencies`, `lend_borrow_transactions`) is dropped;
  - a parent document that does not exist keeps the child waiting.

### Duplicates

Some tables have a natural key (a unique index, listed above), such as an account's name and
last 4. Two records with one natural key, made apart on two devices, are one record.

First the device makes sure the clash is real: the clashing local record may be one the server
already deleted, its tombstone not read yet. Unless that local record has a change queued, the
incoming record waits until the pull is complete, and the device then reads the local record's
document: a tombstone is applied (and the incoming record fits); a live document (or none)
makes the two duplicates:

- The record with the **smaller sync id** (comparing the lowercase hex strings) survives, with
  its own content. The device that sees the clash keeps one local row with that sync id: the
  other row's references are moved to it and the other row is deleted.
- The losing record is deleted everywhere with a tombstone whose `replacedBy` is the
  survivor's sync id; readers follow it for references that still name the loser.

Every device decides the same way, so they agree whichever sees the clash first.

### Seeded categories

Every install seeds the same built-in categories and subcategories. They have fixed sync ids,
so the copies on two devices are one record: the first 16 bytes of SHA-256 over the UTF-8 text
below, as lowercase hex.

- Category: `cashiro-seed:category:<default name>`. `Food & Drinks` →
  `0c96cfa31dcca7a206528958678f18ed`.
- Subcategory: `cashiro-seed:subcategory:<category default name>/<default name>`.
  `Food & Drinks` / `Eating out` → `f03fe7db8f086c901d7a5c9c5a8792d4`.

The default name is the English built-in name (`default_name`), which stays when the user
renames the category. A client that seeds the same defaults must use these ids.

Why fixed ids rather than matching by name at the first sync: matching would only help that
one moment (a device reinstalled later, or an iOS client seeding its own defaults, would add
copies again), it needs a rule for renamed categories, and subcategories have no unique name.
Fixed ids make the defaults the same records from the start, for every client; a rename is then
an ordinary edit of that record.

## Engine

`data/sync`, all in `main` except the Firebase implementation:

| Class | Role |
|---|---|
| `SyncSchema` | The columns of each synced table: kind, reference, natural keys, level |
| `SyncCrypto` | Key derivation, sealing and opening, key check |
| `RemoteStore`, `SyncBackend` | Interfaces: the records of one account; sign-in and the store behind it |
| `SyncLocalStore` | Rows as payloads and back, on the raw database (no entities or converters) |
| `SyncEngine` | Push, pull, held records, duplicates, the first sync |
| `SyncManager` | When to sync, sign-in and passphrase, state for the UI |
| `SyncSettings` | Account, derived key (encrypted preferences), cursor, on/off (`paused`), last result and error |
| `SyncWorker` | WorkManager retry when offline |

### Push

1. Read `sync_outbox` (`pending()`, 400 at a time).
2. For an `UPSERT`, read the row by sync id and build its payload: stored values, decimals in
   plain notation, references as the referenced row's sync id. Encrypt it with the document id.
   A `DELETE` becomes a tombstone (with `replacedBy` from the outbox).
3. Write upserts parents first, then deletes, in batches of at most 400 (Firestore commits up to
   500), with `updatedAt = serverTimestamp()`.
4. `markSent` each entry: one that changed meanwhile stays queued. On failure nothing is marked
   and the next push sends the same records again (writes are idempotent).

### Pull

1. Fetch pages of 300 after the cursor.
2. Apply each page in one Room transaction with `applying_remote = 1`, so nothing is stamped or
   queued; then save the cursor. Deletes first, then upserts (see
   [Order and dependencies](#order-and-dependencies)).
3. Per record: skip it if a local change is queued (see [Conflicts](#conflicts)); decrypt; map
   references to local ids; write it by sync id: update the row that has it, else insert it
   (handling a natural-key clash as in [Duplicates](#duplicates)), or delete it.
4. Retry held records, fetching missing parents as described above.
5. Work balances out again. Every pocket (account, currency) that a remote balance row touched
   is replayed with `recalculateBalancesAfter`. The replay starts from the pocket's row before
   the earliest touched `timestamp`, or from the earliest row if there is none before it. It
   runs the same way as for a back-dated entry, including stopping at a `BALANCE_CALIBRATION`
   row. Capture is on, so rows whose value changes are queued and uploaded. Every device sees
   the same transactions, so every device replays to the same numbers, and the second round
   changes nothing.

Remote data never goes through `AddTransactionUseCase`, `TransactionEditor` or the repository:
balance rows are records like any other and are copied as they are, so no balance moves twice.

### When it runs

- **Local changes**: a push 3 seconds after the outbox last changed (debounced).
- **App to the foreground**: push, then pull, and a snapshot listener while the app stays
  there; a remote write by another device triggers a pull (debounced 1 second).
- **Failures and background**: a failed sync, or going to the background with changes queued,
  enqueues `SyncWorker` (network required, exponential backoff from 30 seconds).
- **Sync now** in the sync page's "status & debug info" section: push, then pull.
- **The switch** on the sync page turns sync off and on (`SyncSettings.paused`). Off pushes and
  pulls nothing and stops listening, but keeps the account, the key, the cursor and the outbox:
  local changes are still queued, and switching it on syncs at once.
- Sync needs no UI, so the app lock does not hold it back; the sync page itself is behind
  the lock like every screen.

### First sync

After sign-in and the passphrase, once per account (`SyncSettings.enabled`):

| Case | Test | What happens |
|---|---|---|
| Cloud empty | no document in `records` | Clear the outbox, queue every local row (`enqueueAll`, parents first), push |
| Cloud has data, this device only the defaults | no rows other than built-in categories and subcategories | Replace local with the cloud (no backup needed) |
| Both have data | otherwise | The user chooses |

- **Use the cloud copy**: exports a backup with the existing exporter into app storage
  (`files/sync_backups`, last three kept), deletes every synced row with capture paused (so no
  tombstones), clears the outbox, inbox and cursor, and pulls everything. It checks the
  connection first, so it fails before deleting anything when offline.
- **Merge**: clears the outbox, pulls everything first, so records both sides have (same sync
  id, a seeded category, or the same natural key) become one; then queues every local record the
  cloud did not have and pushes. Pulling first, rather than pushing first, keeps duplicates of
  shared records out of the cloud.

### Accounts

An account and its balance rows line up on the other device because:

- `accounts` rows carry `name` and `last4`, and the balance rows, transactions, cards and
  templates carry the same strings, all copied verbatim; a rename (`AccountRenamer`) changes all
  of them on one device, and each change syncs as a record.
- `account_id` / `to_account_id` / `transaction_id` are mapped through sync ids to the
  receiver's own row ids.
- The natural key `(name, last4)` makes two accounts created apart under one name one record.

### Not uploaded

Only the 13 tables above. Not: attachments and avatar files (nor their paths), merchant icons,
preferences, the AI provider and key, brokerage connections and tokens, exchange rates.

### Limits and caveats

- **Concurrent offline edits to one account's balance.** Balance rows are snapshots. Two devices
  that each add to the same account while apart each write a row computed from the balance they
  saw. Step 5 of [Pull](#pull) replays the pocket afterwards, so both end on the right balance.
  - The replay writes, and uploads, every later row whose value changes, up to the next
    calibration. A back-dated entry in an account that has never been calibrated can therefore
    rewrite many rows and spend that many Firestore writes.
  - Other clients (iOS) must replay the same way after applying remote balance rows, or their
    numbers drift until the next change.
- **Restoring a backup** while sync is on queues the restored state (phase 1, Backups), which
  then replaces the cloud's copy of those records.
- Tombstones are never removed.
- **Indexes.** Firestore indexes every field by default, and index entries count as stored data.
  - `payload` is ciphertext that is never queried, and indexing it about doubles a record's
    size, so `firestore.indexes.json` exempts it. Queries only use `updatedAt` and the document
    id.
  - Apply the file once per project, with either:
    - `firebase deploy --only firestore:indexes`;
    - Console → Firestore → Indexes → Single field → Add exemption: collection group `records`,
      field `payload`, all indexes off.
  - A record is then about 1 KB.
- Firestore's free tier allows 20000 writes and 50000 reads a day; a first upload writes one
  document per row.

### Code

- `main`: `data/sync/*`, `presentation/ui/features/settings/sync/*` (the sync page, opened from
  Settings → Backup & sync → Firebase sync; route `CloudSync`). Onboarding's **Turn on sync**
  (`OnboardingSyncStep.kt`) reuses its `SyncSetupSection`; a new install that signs in to an
  account with data takes the "this device only the defaults" case of [First sync](#first-sync)
  and skips adding an account.
- The page's "status & debug info" (collapsed) shows the signed-in email and uid, the device id,
  the last sync, the pull cursor, the outbox and inbox counts, the last problem and error
  (`SyncSettings.lastError`), the Firestore project id and the protocol version.
- `standard`: `data/sync/firebase/*` (`FirebaseSyncBackend`, `FirestoreRemoteStore`,
  `FirebaseSyncConfig`), `di/SyncBackendModule.kt`.
- `fdroid`: `di/SyncBackendModule.kt` with `UnavailableSyncBackend`.
- No google-services Gradle plugin: `FirebaseSyncConfig` holds the project's public identifiers
  (project id, app id, API key, web OAuth client id) and `FirebaseSyncBackend` builds a named
  `FirebaseApp` from them, so variants whose applicationId has no client in google-services.json
  (benchmark, profiling) still build. Firestore uses a memory-only cache.
- Tests: `SyncEngineTest` (two in-memory devices and `FakeRemoteStore`), `SyncCryptoTest` (the
  vector), `SyncSchemaTest` (the column list matches the database; seeded ids),
  `MigrationChainTest` (71→72).
