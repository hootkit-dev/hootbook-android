# Core integration

The application owns one `CoreSession` and a ChangeBus. `core-bindings` generates immutable
`dev.hootkit.core.*` records from the pinned Core API; `core-platform` supplies Android native,
Keystore, HTTP, logging and session adapters. The lock and public platform API define the
integration boundary. See [native packaging](core-native-packaging.md).

The original Compose feature UI consumes Core records directly in UiState, with String synced
IDs and `Map<String, X>` joins. Queries use `CoreSession.observe`; commands use `withCore`;
UniFFI service handles close with `use {}`. Core computes all business values. Display helpers
in `presentation/format/` share one money formatter. See [architecture](architecture.md) and
[state management](state-management.md).

Android owns startup, app-lock, locale reconciliation, UI preferences, SAF backup files,
attachment reading, authentication and system entry points. `presentation/app/` contains the
startup gates, and `presentation/data/backup/` orchestrates backup operations. A backup
interrupted during import stays uncertain and is never automatically replayed, because Core
has no durable import receipt query. Restored preferences and assets use the platform applier.

The sole standard flavor supplies Firebase Auth and the Firestore v2 remote adapter. Sync
remains offline until configured and signed in. The installation identity gate blocks switching
to a different UID and blocks remote access when an existing ledger lacks ownership evidence;
offline use and backup export remain available. See [sync](sync.md).

JVM tests use generated-interface fakes for query, cancellation, command and handle-lifetime
contracts. Android instrumentation covers native and platform integration; compilation alone
does not establish device execution.
