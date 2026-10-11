# Core-only final function/source inventory

Current build policy: standard only; see [standard-only.md](standard-only.md) for this delta and fresh validation. Historical migration evidence below retains its original dual-flavor counts and APK hashes.

Scope: App source only; Core/Spec/pin unchanged. The default production path is single Core in every variant. This table lists source implementation; representative UI/native/platform/backend proof and untested combinations remain distinct. Code removal never reads/deletes installed user Room data or original backups. Final evidence and API limits: [core-migration-result.md](core-migration-result.md).

| Area | Retained/new implementation | Removed legacy implementation | Boundary |
|---|---|---|---|
| Account/pocket/calibration/card/history | presentation/core/accounts; generated Core account/StringID data, balanceAt/balanceSeries/cardStatus/anchors/catalog | Room account/card DAO/Entity/Repositories, account-name business inference | Pocket has no independent style field; parent account style is used |
| Transactions/filter/paging/selection/edit/delete/undo/purge | presentation/core/transactions; Core Ledger API, persisted textual drafts and real global filters | old ledger rules/UseCases/balanceEffect/hash/outbox/editor | No synthetic subcategory/amount-range/multi-currency/smallest/merchant-sort filter absent from Core |
| CSV/attachments | Core all-page CSV + system SaveDocument/share; core/attachments copied relative assets | old CsvExporter/AttachmentService/old avatar folders as business storage | Sharing needs narrow core_attachment_share FileProvider path; no committed asset orphan deletion |
| Categories/subcategories | generated Core categories API | Room category tables/name renaming and keyword category classifier | Pure resource icon chooser/artwork/color views kept without Entity inference |
| Budget/plans/subscription/debt | presentation/core/budgets/plans/debts; Core summaries/history/links/settlement | Kotlin budget periods/monthly equivalents/due-date rules/old domain models | Historical category breakdown implemented through Core get(id, historicalPeriod.start, displayCurrency) with exact period validation; history continues beyond24 using Core-returned start cursor. Exact contributing transaction IDs remain absent; no approximate ledger filtering |
| Money/currencies | Core currency settings/rates/convert/custom symbols; pure CoreMoneyPresentation | old rate providers/conversion business service/custom registry/account-name currency defaults | Core supplies values/conversion; approved Home trend only adds/subtracts those read-only results and labels current-FX estimation; no parallel valuation/monthly-equivalent engine |
| AI/bookkeeping | presentation/core/ai + core/ai document boundary; native AI Tools/session | old AI HTTP/chat/tool/ledger implementations | Device document/PDF reading retained; All current AI APIs are implemented; native/Main HTTPS exercised add_transactions+finish and corrected cancel. Advertised8 tools !=8 executed; live provider/billing untested |
| Investments | presentation/core/investments; Core providers/connections/latest snapshot/links/refresh | old BrokerageStore/parser/HTTP/report calculation/models | Only latest snapshot; no old history/connection rename/in-place credential rotation existed |
| Sync/auth/WorkManager | core/sync + flavor CoreCapabilities + system owner bridge | old SyncEngine/localStore/schema/outbox/crypto/backend | standard-only Firebase v2 adapter; missing configuration/sign-in remains offline; Demo SDK Auth/server transaction/read/delete/crossUID deny actually tested with two wrappers sharing one client; independent two-Core sync and Google OAuth untested; known #62/#63/#70 retained |
| Backup/restore | core/backup UI + Core backup/legacy file API + SAF | old BackupExporter/Importer/cloud encryption/Drive/WebDAV/Cashew mapper | No Drive/WebDAV account/provider. Direct Cashew format import is not exposed by current Core. Core has no durable import receipt; interruption remains explicitly uncertain |
| UI/platform prefs | CoreUiPreferencesStore whitelist; five pure AppFont/AppIcon/ThemeStyle/AccentColor/HomeWidget enum files | mixed UserPreferencesRepository/base-currency migration/old AppLockRepository | No business preference mirror; CoreAppLockController owns lock |
| Appearance/profile/merchant icons | new Core appearance/profile; AppStoreIconSearch, IconSwitchingUtils, original theme/icons/images | old mixed profile VM/MerchantIconStore | Platform UI assets are not a second ledger database |
| About/update/licenses/FAQ/review/crash | original pure About/Licenses/GitHubUpdateViewModel/data.update; original external FAQ/guide/privacy/bug/channel links; CrashHandler/CrashActivity | About old SettingsVM and direct legacy datawipe | Optional real Core schema value, no Room-version placeholder. Atomic erase-all without a backup file is not a current Core API, so no fake wipe |
| System entry | Homeowner rewrites receiver/widget/shortcut/tile reminders using Core | legacy Scheduler/SyncWorker removed by separate system owner | Actual permission deny/allow, due scan/notification and production BOOT_COMPLETED rearm plus widget/3tiles/typed-entry evidence; exact-alarm granted/all timezone permutations untested |
| Benchmark | benchmark SeedActivity awaits Core asynchronously, Core NewAccount/ledger.add and pure UI onboarding flag | Room seed DAO/entities/direct balance writes | Strict isolated applicationId prefix; benchmark module/8 production variants compiled, benchmark performance not device-executed. Old-source-copy CI graph has been removed; interrupted seed permits only dedicated benchmark data handling |

## Pure UI closure explicitly retained
- Theme/material color/fonts/layout/icons/effects/adaptive utilities. Semantic transaction colors now accept Core.TransactionKind.
- PureIconSelector extracted from obsolete categories feature; resource chooser has no Category Entity/Repository or account-kind/currency rules.
- IconResource extracted from old CategoryMapping; the old keyword category inference and IconProvider Entity facade are removed.
- InstitutionArtwork retains ONLY47 logo/color/name aliases for visual branding; no currency/region/account classification metadata.
- HomeContentList and40dp TransactionLeadingSlot extracted into pure components with original instrumentation behavior assertions retained; original test runner stays isolated.
- ColorPickerContent/SearchBarBox/visual update dialogs, stateless UI lists/cards/charts whose inputs are already computed values. CurrencyFormatter is display-only and delegates CoreMoneyPresentation; no bank-name currency default.
- Data/update and AppStoreIconSearch remain platform-only Ktor clients; old business data packages are gone. Five pure UI preference enums retained.
- Pure icon/brand/date/theme/update/AppStore tests retained. Engine-specific Room/migration/DAO/old balance/backup/old sync/parser/AI/domain tests are removed, replaced by new Core fixtures in presentation/core and core packages. Public module tests remain unchanged.

## Shared shell dependency cleanup contract
- Remove direct Room/runtime/ktx/compiler/test dependency and KSP room args/schema location. WorkManager may contain INTERNAL transitive Room; this is not an App-owned Room ledger.
- Remove legacy common play-services-auth/Drive usage; keep standard CredentialManager/GoogleId/Firebase dependencies used by new v2 adapter.
- Keep Ktor core/Android/content-negotiation/serialization-json for GitHub updater/AppStore search; Kotlin serialization/Hilt KSP/WorkManager remain.
- Keep PdfBox (new AI reads PDF), image/Compose/pure UI dependencies actually used. No duplicate crypto/backup engine dependency is retained for removed Drive/WebDAV.
- Perf CI must not overlay old main/flavor sources into a new Core dependency graph. Build comparable refs that BOTH contain the coherent Core API and pin, preserve benchmark applicationIdSuffix isolation and report what is compared.

Exact removal/retention source paths and original hashes are in build/core-engine-removal-audit; final graph/commands/outputs are in build/core-final-integration-audit. Do not equate source inventory with online/full-domain acceptance.


## 最终验证与证据矩阵

上轮双 flavor 迁移验收默认、无筛选 forced 单测：standard493（68 fresh XML）、fdroid489（67）、公共14（3），各0 failure/error/skipped、exit0；跨 flavor996 executions /按真实 XML classname#method 去重507。8生产 variant + benchmark module forced编译、普通双debug R8及两release R8已实际通过；不是官方release签名发行或云CI/设备性能验收。

真实代表性设备矩阵覆盖27领域：36个 completed unique method各至少一次literalPASS，跨31runs共85completedexecutions=42PASS/43FAIL，另1frameworkiniterror与3startedunfinished，均保留；不是一轮同APK fresh36全PASS。最新Main业务8/8与AI修复后2/2有实际证据；记录过IME/cardlocator host辅助，不称完全自动driver。Tablet21UI证据断言/14即刻visualPNG另计，不混JUnit。API36ARM64 guest4096；16KB仅静态ELF/APK。

最终这两份普通非Acceptance APK已在自有 API36 ARM64 手机断WiFi/mobile、无clear-data/无provider请求下实际install -r，并回读guestbase.apk SHA精确匹配：冷启动Wallet100→Profile→Home正确→Settings空配置AI→force-stop重开Wallet100。两flavor共10条layoutchecks/6次Home持久化观察，089/101/111PNG实际visual；AI099/110因SECURE黑屏只用layout验证。不是10条JUnit或各flavor全部业务重新测。证据：`build/core-final-device-audit/journeys/final-ordinary-flavors.md` 与 `metadata/final-ordinary-device-verification.json`。

最终详细结果、APK路径/SHA、Core API缺口、已知问题与未测范围见 [core-migration-result.md](core-migration-result.md)。源/命令/字面输出/exit/XML/旧失败/静态检查在 `build/core-final-integration-audit`，设备所选method/raw/source/APK矩阵在 `build/core-final-device-audit/coverage-27.{json,md}`。四角色的实际执行状态以聚合verification.json为准；本段不替代hash-guarded目标前稀疏往返记录。

设备逐领域矩阵记录了UI/native fixture/平台故障/backend fixture及source/APK阶段：账本CRUD/bulk/transfer native命令不伪装UI表单；native seed206之后真实UI分页/筛选/批量/CSV另记；预算Hide/ShowOnHome不证明金融计提暂停。实际受控native-return deliverypause+appkill恢复UNCERTAIN及SaveOriginal同SHA，不证明durable native receipt。平台prefs/assets消费者实际恢复不是Spec限制。未测组合见coverage-27每行Boundary，不能由page navigation或截图推全操作成功。
