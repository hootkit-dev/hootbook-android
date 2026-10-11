# Hootbook architecture

Hootbook keeps the original Compose UI and screen boundaries over a single Rust Core engine.
The standard flavor is the only app flavor. Core owns records, business rules, balances,
budget progress, schedules, debt totals, FX conversion, backup and sync.

Feature ViewModels under `presentation/ui/features/` expose `StateFlow<UiState>` containing
`dev.hootkit.core.*` generated types directly, alongside page state such as filters, selections
and drafts. Synced IDs are `String`; joins use `Map<String, X>` lookups in UiState. Core's local
`i64` brokerage and sync identifiers remain `Long`. There is no UI record mapper layer.

`presentation/data/CoreFlows.kt` provides `CoreSession.observe(changes, tables) { core -> ... }`
for queries refreshed by ChangeBus, and `withCore { core -> ... }` for commands. ViewModels
compose queries, issue commands and manage page state; they do not recompute business values.
UniFFI service handles are closed with `use {}`; the application owns the CoreSession.

`presentation/format/` contains presentation-only money, date and kind formatting. All amount
text uses the single `CoreMoneyPresentation` formatter. Shared startup, app-lock, locale and
preference recovery UI lives in `presentation/app/`; backup orchestration lives in
`presentation/data/backup/`. Platform authentication, files, preferences, system entry points
and lifecycle integration live under `core/` and `core-platform/`.

`HootbookNavHost` hosts the restored feature screens and original adaptive navigation shell.
Unsupported Core data and behaviours are omitted from the UI; incomplete Core conversion
results are shown as partial rather than reconstructed in Kotlin.
