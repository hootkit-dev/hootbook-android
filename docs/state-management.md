# State management

Compose renders immutable feature UiState from a ViewModel `StateFlow` and sends user events
back to that ViewModel. UiState holds generated `dev.hootkit.core.*` records directly plus
page state, drafts and `Map<String, X>` lookup tables. Synced IDs remain `String` throughout
navigation, selections and commands; only Core `i64` IDs use `Long`.

`CoreSession.observe(changes, tables)` immediately queries Core and repeats when relevant
ChangeBus tables change. Its latest-query cancellation prevents stale results from replacing
new filter state. `CoreSession.withCore` runs commands on the shared application session;
service handles close with `use {}`. ViewModels guard repeated submissions and preserve
editable page state with SavedStateHandle where needed.

Core supplies balances, totals, budget progress, FX conversion, subscription schedules and
debt summaries. ViewModels orchestrate those calls without Kotlin business recomputation.
Composables resolve related records from lookup maps; display helpers in `presentation/format/`
format dates, kinds and amounts using the single `CoreMoneyPresentation` formatter.

UI preferences (theme, widget layout, profile and language) remain Android-owned. Core settings
hold business preferences. App startup, lock and locale gates preserve the navigation tree
while access is blocked or locale reconciliation is pending.
