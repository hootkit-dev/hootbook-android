# Original UI on the fixed Core API

This Android-only change restores the original Compose presentation and navigation using generated
Core records directly. The source pin is unchanged: Core `9ca2916045e5d5145776ec75f2ea723e022f0a61`,
Spec `e662bb9c876c52d4672e80da5977bc7e32c664f9`. There are no Room business entities or mirrored
compatibility DTOs. Android owns display formatting, grouping, edit state, platform file handling,
secure callbacks and navigation; Core still owns amounts, balances, conversion, budget accounting,
transaction semantics, import and sync.

## Frozen API gaps

The following original capabilities cannot be faithfully represented by the current Core API.
Their controls are explicitly unavailable rather than filtering a single page or calculating a
second financial model in Android:

- Home's combined net-worth history and account-kind monetary aggregates. Per-account/pocket native
  history and Core account totals remain available.
- Transaction subcategory and amount-range filters, a selected set of multiple currencies, exact
  merchant matching and unsupported sort modes. Existing text/date/account/category/kind/single
  currency filters and Core all-currency totals remain available.
- An exact budget-period transaction list and merchant analytics aggregation. A broad category
  filter is not presented as an exact substitute.
- Legacy quick templates and the default-account preference are not represented by the frozen importer.
  Their exact local counts are shown before a supported-data upgrade; the original values remain in
  the retained recovery snapshot.

## Platform integration limits

The original Google Drive/WebDAV automatic-backup rows remain visible but unavailable in this
integration. File backup export/import still uses the Android document picker, where installed
storage providers can be selected. Cashew import is explicitly unavailable because the fixed Core
has no matching importer; Android does not recreate a conversion/import engine. These are not
claims of complete original-feature parity.

## Existing-data upgrade and omission preview

An existing `pennywise_database` or WAL opens an upgrade screen before the normal Core UI, workers
or widgets can open the shared ledger. The screen first captures and fingerprints a private copy of
DB/WAL, checks the exact v72 schema and integrity, and retains the untouched source files. Only a
separate working copy is opened by SQLite. Decimal text is transported without Android arithmetic.

A compatible `Cashiro Backup v1.0` archive is imported into a unique temporary Core ledger with an
in-memory secure store, no auth/remote, a deny-network transport and isolated attachment/platform
paths. Its actual Core imported/dropped counts are shown along with quick-template/default-account
omissions, missing files, and decoded legacy AI/brokerage input counts. There are no invented user
counts. AI and brokerage settings are read from existing encrypted sources without creating keys or
resetting stores, and handed to the frozen Core importer; inaccessible credentials are explicitly
reported as needing setup. Service credentials never enter UI state or logs.

The user reviews the omission report and explicitly accepts before import into the real ledger.
Core requires an empty destination and remains the only implementation of financial conversion.
The original database, WAL, preferences and attachments are retained. A raw recovery export is also
available through the Android document picker; it is sensitive, but excludes newly converted
plaintext service credential entries and is not itself a Core-importable backup.

The Android journal records the committed Core result before applying platform preferences. An
interruption with uncertain commit status blocks duplicate replay; a known committed import can
resume only the platform step. Completion requires the actual result, platform application and
native shutdown. This is not a claim of a new atomic Core receipt or automatic reconciliation of an
unknown commit. Unknown source schemas and non-empty destination ledgers remain safely blocked.

## CI and SDK

The active build still uses the unchanged pinned Core source. The previously prepared versioned SDK
consumer design is deferred and is not included as a build dependency. Android testing/release signing is
validated before expensive compilation, then checked independently on the APK. Public caches contain
downloaded dependencies only. No Core/Spec edits, real release, merge, secret change or cache deletion
was performed as part of this change.

## Verification

Validation results are recorded for the final candidate in the pull request. Source inspection and
fixture tests are not a claim of screenshot-perfect rendering or emulator execution.

## Feature-page restoration

The following files use generated Core records in the original feature presentation; no Room
entity adapters or alternative financial calculations were added.

| Android feature | Original presentation retained/restored | Fixed-Core differences |
| --- | --- | --- |
| `core/settings/CoreSettingsScreen.kt` | Profile hero, Personal/Finances/Automation/App rounded groups, leading icon circles, currency/default-account/language sheets, original settings destinations | Only supported settings/actions are offered. |
| `core/applock/CoreAppLockSettingsScreen.kt` | Original Data & Privacy Security group, lock switch, animated timeout row and radio sheet | Security callbacks and platform authentication remain the authoritative existing boundary. |
| `core/appearance/CoreAppearanceScreen.kt` | Original illustrated theme/style cards, accent preview, AMOLED/blur controls, launcher-icon previews and font cards | Also retains Core UI preference controls and home-section ordering. No developer-mode entry is restored. |
| `core/profile/CoreProfileScreen.kt` | Banner fade, avatar hero, four overview cells, contacts carousel, avatar presets/gallery/banner/color/name editor | Overview and contact amounts come from Core summaries; incomplete valuations are marked. |
| `core/budgets/CoreBudgetCard.kt`, `CoreBudgetsScreen.kt`, `CoreBudgetSelectionSheets.kt` | Original gradient budget card and compact carousel variant, allowance/progress/days/history presentation, savings/expense wizard and color/amount editor | Exact period transaction membership is unavailable, explicitly explained. Initial limits are supplied to the single Core create command; later limit changes use existing independent Core commands. Detail/history retain the original period divider, category donut, line chart and grouped percentage-ring rows. Original budget routes each created their own SharedTransitionLayout, so no previously active cross-destination shared-element animation is claimed as lost. |
| `core/analytics/CoreAnalyticsScreen.kt`, `CoreAnalyticsCharts.kt` | Original date strip, summary geometry, line/bar/heatmap/pie charts, category rows and large-screen columns | Chart coordinates use Core daily/category summaries. Merchant ranking, daily averages and special investment/loan totals are unavailable and explained; Core net is shown instead of inventing an average. |
| `core/categories/CoreCategoriesScreen.kt` | All/expense/income menu, search surface, icon/category rows and child chips, category preview/type/color/icon editor, migration/delete/reset/hide actions | Category descriptions have no Core field and are explicitly explained. Original custom-category swipe opens the existing delete/migrate confirmation; confirmation waits for Core reference counts. |
| `core/plans/CoreTemplatesScreen.kt` | Original quick-template row geometry, brand icon, edit/delete actions, drag handle and local drag preview; comprehensive editor | Uses Core reorder/delete/template commands; suggestions and all existing Core reference/amount/prefill fields remain. |
| `core/plans/CoreSubscriptionsScreen.kt` | Original total/monthly/yearly card, swipe rows, status/cycle chips, detail/payment sheet, full-screen amount/keypad editor and transaction/subscription switch | Attachments on subscription definitions have no Core support; the screen explains that payment transactions can have attachments. |
| `core/debts/CoreDebtsScreen.kt` | Original total/status/receivable/payable tiles, person/avatar rows, get/owe/overdue tabs, relation search filter, person hero/contact actions, compact record rows and original action dialog, modal person/debt/settlement editors, selection/delete controls | Bulk deletion explicitly invokes separate Core commands. On failure, completed deletions remain and the refreshed remaining rows stay selected; the confirmation explains non-atomic behavior. |
| `core/investments/CoreInvestmentsScreen.kt` | Original net-worth banner styling, refresh/pull-to-refresh, empty-state surface, grouped holding rows and direct create-investment account route | The banner shows authoritative per-account values. Combined investment-only valuation is unavailable and explained, rather than using whole-app net worth or summing in Android. |
| `core/ai/CoreAiScreen.kt` | Original provider shortcuts/model picker, timeline rail, proposal rows/editor, sticky analyze/review/save controls and saved snackbar | Core owns analysis/proposals/apply/undo. Provider changes clear the draft key and require replacing/removing the previously saved credential to prevent reuse at another endpoint. |

Reused original pure presentation components include `CustomTitleTopAppBar`, `NavigationContent`,
`ListItem`, `ListItemPosition`, `HootbookModalBottomSheet`, `ColorPickerContent`, `GenericTypeSwitcher`,
`SearchBarBox`, `SubtitleTag` and the amount/keypad controls. The budget gradient and analytics chart
composables were ported from their original implementations with direct generated-record inputs.

`RestoredFeatureUiTest` adds eleven direct-Core Compose assertions for budget allowance/history routing and limit actions/overspend rings,
category child selection and confirmed swipe, subscription totals/incompleteness, selected-income analytics, person net
and navigation, compact debt record long-press, holding values/navigation, and investment create-vs-manage routing. Focused ViewModel assertions cover provider credential
isolation and partial bulk-delete failure. Their execution status belongs to the aggregate validation
report; these additions alone are not a claim that instrumentation ran.
