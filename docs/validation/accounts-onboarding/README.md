# Accounts and onboarding refresh

## Behavior

- Home now places four compact, equally sized category tiles in a 2×2 grid immediately below Net Worth: Wallets, Banks, Credit Cards, Investments. Each opens a separate category screen; Home no longer expands detail rows or repeats the separate investment shortcut. The existing Accounts widget visibility remains respected, with a fallback position if Net Worth is hidden.
- Category screens display every account in that category as independent lazy rows, without expansion controls. Existing edit, history, balance, visibility and delete actions remain; adding from Wallet/Credit defaults to the relevant type. Settings → Accounts retains the prior all-account management view.
- Investment accounts recognized by the institution catalog are excluded from the bank tile. Investment detail includes brokerage snapshots and access to manual investment accounts. Unknown prices/rates and load errors are not rendered as zero. Manual balances and complete brokerage snapshots both count toward home and profile net worth.
- Tile totals convert into the Net Worth display currency using a checked exchange rate. Converted figures have an approximation mark; missing rates lead to source-currency details instead of relabeling the original number.
- Summaries include all **visible** group members, including collapsed rows; currencies stay separate. Credit-card balances are outstanding debt, not asset totals. Hidden wallets now obey the same visibility rules as bank accounts and credit cards.
- Main account, detail navigation, history, balance editing, merge, visibility, delete, linked-card and orphan-card actions remain available.
- First run offers **Turn on sync** (standard build only), **Import old backup** or **Start fresh**. Import uses the existing ZIP / legacy JSON reader and merge strategy. Success completes setup directly (including backups with no accounts); cancellation leaves the current screen intact. Errors keep setup open for retry, and repeated taps are blocked during import.
- Manual setup is Welcome → Account → Profile → optional Notifications. SMS requests, scanning and tracking screens are no longer part of onboarding. Notification denial/skip still completes setup.
- Turn on sync is Welcome → Sync → Profile → optional Notifications: the Sync step (step 2, in the account step's place) is the sync page's own sign-in, passphrase and first-sync choice (`SyncSetupSection`). Once sync is on it shows what came down and asks the main currency, since preferences do not sync. Continue skips the account step when the device then holds accounts; with none (an empty cloud, the first device) the account step follows and its account syncs. Back from Profile returns to Sync; back is blocked while sign-in or the first sync runs.
- The backup's old setup-complete preference is deliberately not restored during onboarding, so navigation happens only after the importer reports success. Settings imports retain their existing default behavior.

## Performance rationale

Reference reviewed: [InstallerX-Revived HistoryPage](https://github.com/wxxsfxyzm/InstallerX-Revived/blob/main/app/src/main/java/com/rosan/installer/ui/page/main/settings/history/HistoryPage.kt) (`key`, `contentType`) and [ApplyPage](https://github.com/wxxsfxyzm/InstallerX-Revived/blob/main/app/src/main/java/com/rosan/installer/ui/page/main/settings/config/apply/ApplyPage.kt) (`derivedStateOf`). See also [Android's Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices).

Applied here:
1. Logical account keys survive balance-history row ID changes, with separate content types for regular, credit and orphan cards.
2. Partitioning and currency summaries are memoized by accounts and hidden-account keys, outside the lazy-list builder.
3. FAB expanded state is derived from the threshold rather than copied via a coroutine.
4. Lifecycle-aware account state collection; Haze list capture is omitted when blur is off.
5. Home no longer composes all account rows inside one lazy item; every expanded row is independently lazy. Zero-balance accounts remain discoverable.
6. Native Compose fling/overscroll on the account page replaces the custom exponential fling plus nested overscroll wrapper.
7. Compact list rows and static account-card surfaces eliminate per-card infinite tiled-background redraws.

InstallerX also has a baseline-profile module. No generated profile was copied: profiles must be generated against Hootbook's own navigation and release build. No release-device frame-time improvement is claimed from source inspection or debug-emulator testing.

## Verification commands

Use the project's Android SDK/JDK setup and Gradle wrapper:

```sh
./gradlew :app:assembleStandardDebug :app:testStandardDebugUnitTest --no-configuration-cache --console=plain --max-workers=4
```

`--no-configuration-cache` avoids the existing `gitCommitCount` / `gitSha` configuration-time process warning becoming a cache-storage failure. No Gradle version, signing, application ID, database schema or Android runtime dependency versions were changed. The optional palette-generation tooling pins MCU 0.4.0 and esbuild 0.25.10.

Unit tests: `AccountSectionsTest` covers grouping, mixed currencies, exact decimal math, hidden wallets, collapse/expand, empty/small sections and stable identities. `OnboardingStateTest` covers the first-run state, validation, the SMS-free step set and the sync path's steps.

Device checklist: fresh start, cancel picker, invalid file/retry, JSON and ZIP restore, empty-account backup, cold restart, manual entry, notification denial, per-section expansion, hidden accounts, mixed currencies, scrolling, light/dark, larger font.


## MD3 Expressive correction (review follow-up)

The old custom theme aliased primary/secondary/tertiary/error containers to saturated accent colors and hardcoded unrelated surfaces. Those aliases have been replaced, not merely recolored in the preview.

- Every existing accent seed now generates 48 independent MD3 semantic roles for light and dark with Google's Material Color Utilities. `scripts/md3/seeds.json` preserves the accent choices; `npm ci && npm run generate` reproduces the checked-in Kotlin and role manifest. Generation uses the pinned standard tonal-spot specification at normal contrast. This is a valid MD3 palette for an Expressive UI; the separate MCU color variant named `SchemeExpressive` is not a requirement for Material 3 Expressive components.
- Android 12+ dynamic color remains supported. The visible `ThemeStyle` selector is authoritative in the app: an old backup's contradictory legacy dynamic-color flag no longer suppresses a selected system palette. Explicit `HootbookTheme(dynamicColor = false)` still works for previews. System colors are read on recomposition rather than cached across wallpaper-resource changes. AMOLED remains a user-selected dark-mode override.
- `MaterialExpressiveTheme` uses the library's native shape defaults and explicitly selects `MotionScheme.expressive()`; no handcrafted timing curve replaces that scheme.
- Home and Manage Accounts now use native `LargeFlexibleTopAppBar` with its scroll behavior and opaque tonal surfaces, replacing their custom translucent/gradient title bars. Home retains profile, notifications and More actions as native icon buttons. Its obsolete full-list Haze capture is removed with the old header; the separately configured banner effects remain. The extended FAB uses native shape, elevation and color roles, without the custom blur treatment.
- Section toggles, the onboarding primary CTA and back/overflow icon buttons use the Expressive shape-morphing overloads. Summaries and the welcome headline use emphasized typography roles; backup processing uses the native Expressive `LoadingIndicator`.
- Color tests run across all 24 accents in both modes: text contrast >= 4.5:1 for the tested semantic foreground/background pairs, independent accent/container roles, consistent tonal elevation, generated seed fixtures and palette caching. These tests supplement, not replace, device and accessibility review.

Primary references: [MaterialExpressiveTheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable), [MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme), [Material Color Utilities](https://github.com/material-foundation/material-color-utilities/blob/main/dev_guide/creating_color_scheme.md). Component signatures and default token behavior were also checked against the actual Material3 1.5.0-alpha12 sources used by this project.


## Per-screen review gate

The user requested a visual review before pushing. Changes remain local until the user explicitly approves the screenshot set. The review includes account sections, expansion states, add/edit sheets, first-run steps, import picker and failure state, major existing pages and fixed/system light/dark comparisons. Native `HootbookThemeTest` checks actual Android system roles in light/dark plus fixed colors and the preview opt-out; it complements the four account UI tests.


## All-account summary-only and elevation review revision

Applied to wallets, banks and credit cards on both Home and Manage Accounts. Each nonempty summary remains composed when collapsed (`accounts.isNotEmpty`, not the visible-row list), with independent saved expansion state. Totals do not depend on expansion. Summary surfaces, Home rows, Manage Accounts cards and full detail-preview cards share a restrained 2dp native elevation token. Semantic container roles, system dynamic colors, lazy rows and static surfaces remain intact; no extra blur or perpetual animation was introduced.

Unit cases cover all three summary-only groups, hidden members, currencies and small/empty groups. Four native group UI cases cover wallet/bank/card arrows plus footer collapse. Updated emulator screenshots replace affected states in the review page. Pushing remains gated on explicit user approval.

## Four-category overview revision

AccountOverviewTest adds ten cases for exclusive categorization, empty/unconnected states, mixed currency precision, missing rates, negative credit balances, snapshot values, source deduplication policy, unknown valuations and storage failures. AccountOverviewGridTest checks the 2×2 arrangement, four independent click destinations, narrow/large-text interaction and the connection action instead of a fake zero. Device review additionally exercises the actual routes and wallet/card creation defaults. Repeated account surfaces now use 0dp elevation and semantic tonal separation; floating actions and standalone previews retain their native/previous elevation. Dynamic light/dark colors remain intact. All new category screens preserve the existing top-level navigation and review-before-push gate.

## Selective elevation follow-up

Per the latest review, Home tiles, category summaries, account rows and investment cards are flat (AccountSurfaceElevation = 0dp). Spacing and surface-container roles provide hierarchy. Floating action buttons retain native elevation; the existing single-card form/detail preview may retain 2dp. No navigation, amounts or data behavior changes.

## Contained net-worth hierarchy revision

Net Worth and its four category entrances now share one flat outer Surface and one Home lazy item. The total uses the largest typography; category entries use transparent backgrounds and smaller type inside that surface, separated from the header by a subtle divider. Category taps remain independent of the balance header's existing history expansion and currency selection. When Net Worth is hidden in widget preferences, the standalone category grid remains available. Debt is displayed as a negative contribution; a credit refund is positive. Investment snapshots are included in net worth. Added tests verify containment, independent hit targets, large text and signed contributions without changing stored balances.

### Currency action revision
The expanded net-worth currency selector now uses the native expressive FilledTonalButton shapes, neutral semantic surfaceContainerHigh/onSurfaceVariant colors, labelLarge text and an 18dp trailing chevron. Removed the custom tall small-radius cyan Surface, border and 12dp left indent. Native minimum touch target remains enabled; content can grow with font scale. Currency picker callback and conversion behavior are unchanged. Added click and narrow-layout/2x-font native tests.

### Independent list-led design revision
Home replaces the 2x2 tiles with one four-row classification list below a highlighted net-worth summary. Each row contains a plain category icon, title, account count/status, value and forward chevron. At narrow widths, large font scales, or long values the amount moves below the labels instead of being auto-shrunk. Overview conversion and investment inclusion logic are unchanged.
Category pages use transparent summary headers and continuous flat account rows with inset dividers, wrapping names and separate management menus. All-account management keeps only its header disclosure control, removing duplicate footer actions. Investment summaries, empty-state description and holdings also use flat page structure. Currency picker, category routes, add defaults, dynamic color and native floating actions remain intact. Added account-row navigation/menu independence and 2x-font tests; updated layout tests to assert four equal-width vertical rows.

### User-phone Wallet reference revision
Adapted account and classification rows from the Wallet transaction row the user showed on their connected phone, not from the older web reference. Rows use surfaceContainerLowest, large corners, no border/elevation, 6dp gaps, and a 40dp small-corner icon base with a 20–24dp mark. WalletStyleRow aligns names and amounts by the first text baseline; currency/account identifiers are secondary. Font scaling and long values switch to a stacked layout without shrinking text. Explicit account management menus remain separate 48dp native buttons; this is a deliberate adaptation rather than copying a transaction-only row. Home category arrows and long divider rules are removed; summaries, navigation, data and dynamic colors remain unchanged. Added a native baseline-alignment test. Only emulator-5554 receives the debug build; the connected phone and its personal reference screenshot are not included in exports.

### Tonal row color refinement
Following user review, changed account/classification row fills from surfaceContainerLowest to surfaceContainerLow, and the small icon base from surfaceContainer to surfaceContainerHigh. This keeps the row in the same tonal family as the page instead of appearing pure white in light mode. Layout, typography, spacing, behavior and stored data are unchanged; both roles still follow fixed/system light/dark schemes.
