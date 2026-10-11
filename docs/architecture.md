# Hootbook Architecture

Current production uses the single Rust Core path and the standard flavor only.
See [Core migration architecture](core-integration.md) and [standard-only build notes](standard-only.md).
The legacy layer inventory below records the pre-Core implementation; it is not a second production engine.

Hootbook is a single-module Android app (`app`, package `io.github.hootkit_dev.hootbook`) for manual,
multi-currency bookkeeping. It uses MVVM with unidirectional data flow: Room is the source of
truth, repositories write to it, ViewModels expose `StateFlow`s, and Compose renders them.

## Layers

```
io.github.hootkit_dev.hootbook
├── presentation   Compose screens, feature ViewModels, navigation, theme
├── domain         Use cases and pure models (budget periods, card cycles, net worth)
├── data           Room, repositories, preferences, backup, cloud, AI, brokerage, currency
├── di             Hilt modules
└── utils          Formatting (DateFormats, CurrencyFormatter) and small helpers
```

### Presentation

- One package per feature under `presentation/ui/features/`: home, transactions, add,
  accounts, analytics, budgets, categories, subscriptions, lendborrow, investments, ai,
  profile, onboarding and settings.
- A ViewModel per screen exposes a UI state `StateFlow`. Screens send events back as
  ViewModel calls.
- Navigation: type-safe routes in `presentation/navigation/HootbookDestinations.kt`, hosted by
  `HootbookNavHost`.
- Adaptive layout in `presentation/ui/adaptive/WindowLayout.kt`:
  - Below 600 dp: bottom bar.
  - From 600 dp: navigation rail.
  - From 720 dp: list-detail panes for transactions and accounts.
- Shared Material 3 Expressive wrappers live in `presentation/ui/components` (see `docs/design.md`).

### Domain

- Use cases that write the ledger:
  - `AddTransactionUseCase`: one database transaction per add.
  - `AddSubscriptionUseCase`.
  - The lend/borrow use cases.
- Pure models with their own unit tests:
  - `BudgetPeriods` (rolling budget windows; `counts()` decides what a budget includes).
  - `CardCycle` (statement and due dates).
  - `netWorthByDay`.

### Data

- **Database**: `HootbookDatabase` (version 72).
  - Migrations are in `data/database/Migrations.kt`, listed by `HootbookDatabase.MIGRATIONS`.
  - First-run seeding is in `DatabaseCallback.kt`.
  - Sync capture: synced tables carry `sync_id` / `sync_updated_at`, and SQLite triggers
    (`SyncTriggers`) queue every change in `sync_outbox`. See [sync.md](sync.md).
- **Accounts**:
  - An account is a row in `accounts` and holds one or more currencies in `account_currencies`.
  - Balance history is `account_balances`, one row per change, per currency ("pocket").
  - Transactions point at `account_id`, and transfers also at `to_account_id` / `to_currency`.
- **Balance effects**:
  - `balanceMoves` defines how a transaction moves balances, and `AddTransactionUseCase` and
    `TransactionEditor` both use it. An edit undoes the old effect as a delete does, then
    applies the new one.
  - `AccountRenamer` and `CategoryRenamer` carry a rename to every table that refers to the
    old name, in one transaction.
- **Backup** (`data/backup`):
  - A zip holding `backup.json` plus attachments, merchant icons and, optionally,
    brokerage connections.
  - A merge import adds only what is new: transactions by hash, and balance rows of new
    transactions or new accounts.
- **Cloud** (`data/cloud`): manual full backups to Google Drive or WebDAV (`CloudBackupManager`:
  upload, list, restore, retention), end-to-end encrypted when the user sets a passphrase. No
  schedule and no snapshot sync: multi-device sync is Firebase (below). Both share Settings →
  Backup & sync.
- **Sync** (`data/sync`, [sync.md](sync.md)): multi-device sync, end-to-end encrypted.
  - `SyncEngine` pushes the outbox to a `RemoteStore` and applies remote records in one
    transaction per page with capture paused, copying balance rows verbatim (never through the
    use cases). `SyncManager` decides when (debounced push, pull in the foreground, WorkManager
    retries) and drives sign-in, the passphrase and the first sync.
  - The sole standard flavor binds `CoreCapabilitiesModule` and the Firebase v2/Auth adapter
    under `src/standard`; no separate flavor or unavailable flavor stub remains.
    With no valid Google/Firebase configuration or sign-in, standard remains offline.
- **AI** (`data/ai`): cloud models with the user's key. `LedgerTools` only queues proposals;
  nothing is written before the user saves the review.
- **Brokerage** (`data/brokerage`): read-only IBKR Flex holdings, kept apart from the ledger
  (`docs/brokerage-connections.md`).

## Conventions

- Writes that touch several tables run in `database.withTransaction`.
- Amounts are `BigDecimal`. Each transaction keeps its own currency, and conversion happens only
  for display and totals (`CurrencyConversionService`).
- Dates are shown through `DateFormats`, e.g. `9月1日` in Chinese.
- Date pickers work in UTC midnights: use `toPickerMillis()` / `pickerDate()`.
- User-visible text is in string resources. `values`, `values-zh` and `values-zh-rTW` are
  maintained.

## Testing

- Unit tests run on the JVM (Robolectric with in-memory Room where a database is needed):
  `./gradlew :app:testStandardDebugUnitTest`.
- `MigrationChainTest` builds every exported schema and opens it at the current version.
- `BackupMergeTest` checks that merging the same data twice adds nothing.
- `SyncEngineTest` syncs two in-memory databases through a fake `RemoteStore`; `SyncCryptoTest`
  holds the protocol's encryption test vector.
- Performance is measured on a physical device with the `benchmark` module (see `CLAUDE.md` →
  Performance).
- CI (`.github/workflows/test.yml`) runs standard unit tests/debug lint and compiles/packages
  standard instrumentation tests, plus separate public Core module validation.
