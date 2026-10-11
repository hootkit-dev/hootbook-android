# Rust Core 接入：当前生产架构与历史验证

> 历史迁移验收包含当时真实的双 flavor 编译、单测和设备记录。当前仅 standard，
> 本次增量与 fresh 验证见 [standard-only.md](standard-only.md)；下文旧任务、SHA、计数不代表当前 variant。

## 当前生产架构与最终验证摘要

所有正常 flavor/buildType 默认唯一 Rust Core，temporary CORE_ONLY/legacyComponentsEnabled、App-owned Room/旧业务/sync/AI/broker/backup/Drive-WebDAV及直接专属依赖已移除。App保留Compose、平台偏好/认证/系统入口及实际纯展示/更新/PDF能力，业务只来自生成Core API。未自动读取或删除用户旧数据库、原备份；WorkManager内部传递Room不是旧账本。

当前仅 standard，提供 FirebaseV2/Auth 回调；未配置或未完成授权时仍为正常离线状态。每安装固定UID、既有库缺身份witness闭门是App产品限制，不是修好Core #70。Core/Spec/pin保持原值；#62/#63/#70主文件行号及SHA可读证据见 core-migration-result.md，不冒独立cargo复现。

Home的Currency.convert、预算历史>24与历史分类精确Core period已实现；平台偏好与图片已实际导出/恢复。准确预算交易IDs、advancedfilters/sorts、专门金融统计、全局历史净资产、Cashew特殊导入、atomicpreservingreset、durableimportreceipt不由Kotlin第二引擎伪造。CORE_COMMITTED只重试平台，UNCERTAIN保留源/安全副本与导出、不自动重放。

App包装名/runner/nav及AIcancel Main重入问题均已实际发现并App-only修复：libuniffi_hootkit_core.so与生成loader一致、runner确有DEX类定义、onboarding后tabanchoring、nativealways-dispatch与pointerowner。Core/Spec生成配置不改；真实首次失败与修正后设备证据分开。

显式-PcoreAcceptance -PrealAppInstrumentation只用于dedicated debug .coreacceptance/demo fixture；普通release/preview/benchmark/profiling和普通最终APK均关闭。受控AI信任只是TEST-only替同一transport client并finally恢复，Firebase仅demoSDK同client两个backend wrappers，不是线上服务或两Core同步。perf CI不拼接旧Room源码到新图。

上轮迁移验收默认、无筛选 forced 单测：standard493（68 fresh XML）、fdroid489（67）、公共14（3），各0 failure/error/skipped、exit0；跨 flavor996 executions /按真实 XML classname#method 去重507。8生产 variant + benchmark module forced编译、普通双debug R8及两release R8已实际通过；不是官方release签名发行或云CI/设备性能验收。

真实代表性设备矩阵覆盖27领域：36个 completed unique method各至少一次literalPASS，跨31runs共85completedexecutions=42PASS/43FAIL，另1frameworkiniterror与3startedunfinished，均保留；不是一轮同APK fresh36全PASS。最新Main业务8/8与AI修复后2/2有实际证据；记录过IME/cardlocator host辅助，不称完全自动driver。Tablet21UI证据断言/14即刻visualPNG另计，不混JUnit。API36ARM64 guest4096；16KB仅静态ELF/APK。

最终这两份普通非Acceptance APK已在自有 API36 ARM64 手机断WiFi/mobile、无clear-data/无provider请求下实际install -r，并回读guestbase.apk SHA精确匹配：冷启动Wallet100→Profile→Home正确→Settings空配置AI→force-stop重开Wallet100。两flavor共10条layoutchecks/6次Home持久化观察，089/101/111PNG实际visual；AI099/110因SECURE黑屏只用layout验证。不是10条JUnit或各flavor全部业务重新测。证据：`build/core-final-device-audit/journeys/final-ordinary-flavors.md` 与 `metadata/final-ordinary-device-verification.json`。

最终详细结果、APK路径/SHA、Core API缺口、已知问题与未测范围见 [core-migration-result.md](core-migration-result.md)。源/命令/字面输出/exit/XML/旧失败/静态检查在 `build/core-final-integration-audit`，设备所选method/raw/source/APK矩阵在 `build/core-final-device-audit/coverage-27.{json,md}`。四角色的实际执行状态以聚合verification.json为准；本段不替代hash-guarded目标前稀疏往返记录。

## 以下为历史批次记录（含旧 debug-only 入口与阶段性限制，不代表当前状态）

## 依赖与来源

- `:core-platform` → `:core-bindings`；公共模块不依赖 Firebase。
- `core-bindings/core.lock.json` 固定 Core / Spec 提交、UniFFI / Rust / NDK 版本、UDL / 配置 / Cargo.lock 哈希。
- 默认源码目录是同级 `../hootkit-core`；可以传 `-PhootkitCoreDir=/absolute/path`。缺失时先取得固定提交，构建不会偷偷下载或修改共享核心。
- `scripts/core/build.py` 拒绝提交不一致、tracked 修改、锁文件哈希不一致和 Rust 版本不一致。
- 生成的 Kotlin 和四 ABI `.so` 在 `core-bindings/build/generated`，不手写或提交生成业务类型。
- JNA 5.17.0 AAR 含第二轮 Android 16 KB 修复；来源：https://github.com/java-native-access/jna/blob/5.17.0/CHANGES.md 。Rust ELF 的架构和每个 PT_LOAD 的 16 KB 对齐由构建脚本检查。

## 构建环境

需要 Python 3.9+、JDK 21、Android SDK 37、NDK 28.2.13676358、Rust 1.97.0。

```bash
rustup toolchain install 1.97.0 --profile minimal
rustup target add --toolchain 1.97.0 \
  aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
export ANDROID_HOME=/absolute/path/to/android/sdk
export RUSTUP_TOOLCHAIN=1.97.0
python3 scripts/core/build.py --verify-only
./gradlew :core-platform:testDebugUnitTest :core-platform:compileDebugAndroidTestKotlin
./gradlew :core-bindings:assembleDebug :core-platform:assembleDebug
```

如使用自定义工具链位置，可设置 `CARGO` 和 `RUSTC` 的绝对路径以及 `RUSTUP_HOME`。
ABI 库随 JNI 合并任务构建，单纯编译 Kotlin / 单元测试不会启动 Android native 构建。
Native provenance 记录四 ABI SHA-256、文件大小和 ELF 对齐；bindings provenance 记录生成代码 SHA-256。

## 平台实现

- **CoreSession**：application-owned 异步初始化；多个等待者复用同一次打开；屏幕取消不取消打开；打开失败显式重试；关闭等待打开完成，先 `shutdown()` 再释放绑定 handle。App 接入时只注入一个会话，并跳过 `:crash` 进程。
- **ChangeBus**：每张表的 revision 状态，订阅得到首次快照；无人订阅和高频合并时仍保留失效；后台回调不查库。
- **KeystoreSecureStore**：AndroidKeyStore AES-GCM，键名参与 AAD，AtomicFile + fsync，存储位于 noBackupFilesDir。解密失败视为缺失，临时存储失败返回 PlatformError.Unavailable；不含明文降级。
- **OkHttpTransport**：HTTPS-only，重复 header，整体超时，重定向开关，流式响应上限，取消传递给网络请求；HTTP 4xx/5xx 正常返回；诊断不含请求内容。
- **AndroidCoreFactory**：`getDatabasePath("hootkit.sqlite")`，附件和备份放 noBackupFilesDir/hootkit-core，正式 fixedNow 为 null；auth/remote 成对注入或同时为空。

## 验证边界

单元测试验证 ChangeBus、会话并发与取消、HTTPS 适配契约；不在 JVM 上冒充 Android native 加载成功。
`CoreSmokeTest` 是设备端测试：版本查询、建账户、记账、余额、关闭重开以及 Keystore 持久化。

```bash
./gradlew :core-platform:connectedDebugAndroidTest
```

没有连接设备时只编译上述测试；发布前还需真机 / 模拟器运行、最终 App R8 与 APK/AAB 16 KB 对齐检查。
本批已补全系统备份排除规则（数据库、WAL、SHM）。首次集成 App 前还需将 Hilt、flavor 认证和前后台／后台任务接线加入应用层。

## 第二批：隔离 Core UI（不是生产切换）

```bash
./gradlew :app:assembleStandardDebug -PcoreOnly --no-configuration-cache
./gradlew :app:assembleFdroidDebug -PcoreOnly --no-configuration-cache
```

- `CORE_ONLY` 默认 false，仅 debug build type 允许 `-PcoreOnly` 设置为 true。release、preview、benchmark、profiling 继续 false，CI 发布渠道、applicationId 与签名不改变。
- App 入口通过 Provider 延迟旧依赖；Core-only 分支先等待单一会话就绪，首页只导航到新账户、分类、交易、预算、订阅、模板、人员借贷、设置、币种汇率和文件备份页，返回不通往旧 UI。
- 新页面直接使用生成 Core 类型/API，字符串 ID 贯穿路由，账户路由参数 URI 编码；Hilt ViewModel 由每个 NavBackStackEntry 持有。
- 新首页提供净资产与账本合计，不是旧首页全部功能的等价替换；金额来自 Core，`complete=false` 明确标识不完整换算。
- Core-only 屏蔽旧提醒/Boot/Widget/Tile 组件；旧 SyncWorker 构造仅拿 Provider，执行在访问旧引擎前退出。旧动态 shortcut 清除，不读取旧数据库。
- API 26/27 通过 ActivityManager/`/proc/self/cmdline` 识别进程，API 28+ 使用 Application.getProcessName；`:crash` 或身份未知时不打开 Core。
- 当前两个 flavor 都离线：standard 的真实 AuthProvider/Firestore v2 RemoteStore 尚待实现，UI 明确显示待接入；fdroid 保持无 Firebase 依赖。
- 本轮不读 Room、不自动读取旧数据库、不调用旧账本导出；生产全量删除旧引擎/Drive/WebDAV 尚在后续批次。

### 本轮页面边界

账户/口袋、余额锚点、分类/子分类、交易增改/软删/撤销，以及本轮预算/订阅/模板/人员借贷管理已形成隔离验证入口；历史曲线、账户图标选择、完整旧首页视觉等价、交易新附件选择、交易批量操作/永久删除/导出与同步仍在后续范围；文件备份由第四批接入。旧数据迁移只允许新 Core 接口导入旧格式备份，不保留旧引擎门面。

### R8 的 JNA Desktop 类型边界

最终 App R8 曾报告 JNA `Native$AWT` 引用的四个 Android 不存在的 Desktop 类型（`java.awt.Component`、`GraphicsEnvironment`、`HeadlessException`、`Window`）。Consumer rules 仅对这四类加精确 `-dontwarn`；不使用 `-ignorewarnings` 或 `java.**` 泛化，不削弱生成 Core/JNA/native/callback 的反射保护。实际生成绑定和 Android 宿主不调用 AWT 窗口入口；这是 R8 解析 optional Desktop 类型的处理，不是修补 Core 缺失或设备 native 加载证明。

### 测试分层

沿用现有 Hilt、JUnit4、kotlinx-coroutines-test、Robolectric 与 Compose，不新增另一套测试框架。输入格式边界和状态机在 JVM 验证；ViewModel 的生成接口 fake 使用 Robolectric API 28 下的 `NoPointer` 句柄，以观测取消、过期查询、防重提交、返回顺序和服务句柄释放，fake 不加载 Android native。Application-owned Core handle 不由页面关闭。

真实 JNA/native 加载、Keystore 和重开账本由现有 `CoreSmokeTest` Android instrumentation 验证；本轮缺少连接设备，只编译测试 APK/源码。APK/R8/ELF 检查只能证明打包和反射标识，不证明设备运行成功。

### CI 接线

App 现已依赖 Core 公共模块；所有 app 构建工作流通过 `.github/actions/setup-core` 读取 lock、取得 pinned Core 源码并安装 Rust targets/NDK。`ORG_GRADLE_PROJECT_hootkitCoreDir` 指向 workflow checkout，无构建时隐式源码下载。测试工作流覆盖默认路径编译与 `coreOnly` 路径回归；发布工作流不启用 `coreOnly`。

perf baseline 保留“旧 App 主/Flavor 源码 + 当前构建基础设施”的原有比较策略：main、standard、fdroid 一起替换，避免当前 flavor DI 引用旧 main 不存在的 Core 类型。Core lock、公共模块、构建脚本和 setup action 纳入缓存标识。这不是完整历史提交构建，也不是 Core-only 性能测量；benchmark/profiling 均不切换入口。

## 第二批本机已验证结果（历史记录）

- 两 flavor 的 Core-only 与默认 debug Kotlin 编译通过；默认生成的 `CORE_ONLY=false` 已逐个检查。
- 新 App 52 项测试（宿主 12、账户分类 22、交易首页 18）与公共模块 14 项测试通过；公共测试已强制重跑，而非仅依赖缓存。
- standard `-PcoreOnly -PslimDebug` 最终 R8 APK 构建通过；实际 APK 含 arm64-v8a Core/JNA、Core native 与 pin provenance 字节相同、每个已打包 64-bit ELF PT_LOAD 16 KB 对齐、UniFFI/JNA callback 反射 DEX 标识保留、zipalign `-P 16` 通过。
- 最终 APK 六个旧提醒/Widget/Tile 组件均 disabled，applicationId 未改变；本机 APK 使用 Android Debug key，不是发布签名验收。
- Core 四 ABI AAR/provenance 与 instrumentation 编译/测试 APK 打包检查通过；没有连接设备，设备 native/Keystore/UI 测试仍待运行。
- YAML/CI setup、source isolation 与 diff whitespace 静态检查通过；未在 GitHub Actions 或设备性能平台实际运行工作流。

精确命令、环境、原始输出与 exit status 存于 `build/core-adoption-integration-audit`；该目录是本轮审计，不覆盖首批审计。

## 下一批

1. 设备端执行 Core-only UI/native/Keystore 场景，补齐本批页面交互和旧功能缺口；当前本机无设备时不以 JVM 测试冒充设备验收。
2. 接入其余业务：AI、券商、完整首页/分析；完善本轮页面视觉与附件/头像/系统入口。
3. 验收第四批系统文件备份并接入 Firestore v2，处理已记录的同步/账号隔离问题。
4. 完成系统入口接入与最终生产切换，删除 Room、旧业务类型/Repository、旧同步/备份及 Drive/WebDAV 全部专属代码；不引入双引擎生产运行。

## 第三批：预算／计划／借贷与交易联动

本批仍是 debug `-PcoreOnly`，没有切换默认生产入口，也没有连接 Firebase。
预算、订阅、模板、人员与借贷均直调生成 Core API；四项首页菜单只进入新页面。

- **预算**：首页只展示 `budgets.list(today, homeOnly=true)` 返回的当前汇总，预算页的历史最多取 24 个周期。Core 没有“实际计入预算的交易 ID”或“历史分类细分”接口；普通 ledger 筛选不是准确预算明细，本批不伪装支持。
- **订阅付款**：导航携带 URI 编码的 String subscriptionId；交易页读 `plans.subscriptions(today, true)` 并预填支出、金额/币种、账户/分类、商户/备注和订阅引用。缺账户/分类由用户补全，日期使用当前本地日期时间且可修改。保存只执行一次 `ledger.add(draft)`，不再调用 `markSubscriptionPaid`，付款状态更新归 Core 原子命令。单独“只标记付款”依然是订阅页明确的另一项操作。
- **模板**：导航携带模板 ID，由 `plans.templatePrefill` 生成预填。kind/merchant/category/subcategory 直接覆盖，空备注清空；amount/account/currency 为空时保留草稿原值；日期不变，附件/订阅引用和转账目的字段保留。只用于新增交易，不套用到已存在交易。过期响应通过 source revision 阻止覆盖用户输入、更新来源或已返回的草稿。
- **人员借贷**：债务详情携带原 `DebtEntry.transactionId` 回新交易详情；交易详情选择人员和借贷类型，调用 `plans.linkTransaction(existingId, personId, kind)`，不再次 `ledger.add`。Core 负责借贷校验、账户余额、结清和删/解绑行为；删除关联记录与解绑的不同语义在确认 UI 中明确显示。
- **首页**：净资产、交易合计、预算进度、`plans.subscriptionSummary(today)`、`plans.debtSummary(today)` 各自原样显示；不把债务净额加到净资产，不在 UI 重算汇率/预算/到期业务。complete/missingCurrencies 显示部分换算状态；依赖所有相关账户/交易/预算/分类/订阅/人员/债务/汇率/设置 revision，ON_RESUME 重新查询日期敏感数据。
- **币种**：选择账户不对已填写金额隐式重新标币种；订阅金额与币种保持配对。更换币种只能是用户明确操作，跨币种转账到账金额不由 UI 推算。

CI 回归 package wildcard 包含新增 budgets/plans/debts 和 home/transactions 集成测试，保留 pinned Core setup action；不硬编码测试数量。测试结果以 XML 实际 case 数及审计记录为准。
输入/状态机与调用次数测试仍用生成接口 fake；不据此宣称设备 native、线上 Firebase、生产切换或全量删除旧引擎已经完成。

本批精确 baseline/modified 命令与字面输出、独占 before/hash、patch、修改快照和可执行 rollback 见 `build/core-business-integration-audit`；各 feature 另有独立审计目录。本轮设备测试尚未执行，最终精确执行状态由 verification.json 记录。

### 第三批集成与独立验证结果

- Core-only standard/fdroid Kotlin 已实际编译通过；第二次验证同两项任务 up-to-date，记录中保留首次实际编译输出。
- 第二次实际执行 App 定向测试 **130 项全部通过**（原 52、feature 新增 61、集成新增 17；依据 XML，不是仅源码计数）。独立阶段又强制重跑公共模块 **14 项全部通过**，本轮实际合计 **144 项通过，0 failure/error/skipped**。其中输入/状态机全部为 JVM fake 测试，不代表设备 native 或 UI 场景验收。
- 首次同 130 项测试有 2 个借贷失败：测试 JDK Proxy 把同步抛出的 checked CoreException 包装成 UndeclaredThrowableException；修正测试 seam 为 suspend Continuation 失败恢复，未改生产处理，也未删减 missing-person/orphan 断言。首次失败日志与 XML 完整保留。
- 独立阶段实际完成默认入口 standard/fdroid Kotlin 编译，检查两者 `CORE_ONLY=false`；两 flavor `-PcoreOnly -PslimDebug` R8/APK 构建通过。standard APK 实际含 `arm64-v8a`，fdroid 延续既有合并 ABI 配置，实际含 `arm64-v8a` 与 `armeabi-v7a`，并非仅 arm64。已检查 Core/JNA ABI 覆盖、Core native 字节与 pinned provenance 一致、全部已打包 64-bit ELF 的 16 KB PT_LOAD 对齐、两 APK `zipalign -P 16`、生成业务类型/JNA/callback DEX 标识及新业务 ViewModel R8 mapping。两 APK 的六个旧系统组件均禁用；fdroid 无 Firebase DEX 类型。这里验证的是 debug 签名产物，不是发布签名或设备加载验收。
- Core instrumentation 源码编译、测试 APK 打包和 Core AAR 成功；没有执行 connected tests。额外尝试旧 App `compileStandardDebugAndroidTestKotlin` 失败，原因是既有测试与旧组件 API 漂移（如 `hideLabels`、`formatBalance`、`onMoreClick`）。13 个旧 App Kotlin instrumentation 测试文件和涉及的 4 个旧组件已逐个验证与 HEAD 字节一致，本轮未改它们，也未删除测试或扩大改动范围来修复这个旧问题；失败命令与输出保留。
- 静态 CI/YAML、pinned source setup、生产 flags、baseline source-set 一致性及新 UI 业务隔离检查通过；尚未在云 CI、真机/模拟器运行新 Core native/UI/Keystore 或性能场景。
- 独立阶段精确命令、环境、字面输出、退出状态、测试 XML 和 APK 检查记录见 `build/core-business-review-audit`。整批 34 路径的真实 rollback/baseline/reapply/modified 验证将由独立审查最终记录确认，此处不预先宣称往返已经完成。
- 默认生产入口仍旧、旧引擎/Room/Drive/WebDAV 未删除；Core-only 两 flavor 仍离线，文件备份、Firestore、附件/头像选择、系统入口与其余业务仍待后续。


## 第四批：业务设置／币种汇率／系统文件备份

本批仍为 debug `-PcoreOnly` 开发入口，默认生产入口未切换，两个 flavor 仍离线。首页与设置页新增的导航仅指向新 Core 页面，不连接旧引擎、旧云备份或旧业务设置。

- **业务设置与汇率**：Core Settings/Currencies API 管理主货币、默认账户、货币与汇率；Android 只保留独立 UI 语言偏好。币种路由可携带 URI 编码的字符串 currency，生成类型直接进入页面状态。
- **冷启动语言**：独立 `hootkit_core_ui` 偏好保存语言选择；无记录时通过官方 `LocaleManagerCompat.getApplicationLocales(context)` 一次迁移 Android 的纯 UI 语言。CoreHostModule 打开 Core 读取同一持久选择，不等待 AppCompat 异步恢复；Core-only Activity 在 attachBaseContext 前应用它。API33 的 OS per-app 语言是权威选择，小 store 仅作 mirror；系统跟随模式明确保存空选择。活会话遇到配置语言变更时，Ready 层串行协调 `settings.setLocale`，成功前遮挡并禁用账本，失败显示重试；不重开根 Core、不按 ChangeBus 驱动、不移除 NavHost，保存导航栈和草稿；相同语言不重复提交。语言设置失败补偿 Android 偏好和 Core locale，不读取旧账本偏好。
- **文件备份**：Core 实现导出、检查、新格式 Merge/ReplaceAll 和旧格式导入；Android SAF 负责选取/保存文件，新备份 UI 只给 `cacheDir/core-backup-share/<UUID>.hkbak` 中的副本生成分享 URI。新增的授权路径为 `core_backup_share` / `core-backup-share/`；导入源副本和 journal 位于 noBackup，不属于现有 cache/files 授权根。既有 `crash_logs` 的 `cache-path="."` 仍授权整个 cache，这是历史边界，本批未改旧崩溃日志出口，不宣称整个 FileProvider 已收窄。分享携带读权限和 ClipData，副本在 24 小时后可清理，不在分享后立即删除。
- **恢复状态**：源副本及恢复日志长期位于 `noBackupFilesDir/hootkit-core/backup-ui/<UUID>`，不自动删除原 URI、源副本或导入回执。Core 返回后的恢复报告持久化，再做平台文件处理；IMPORTING 状态重启属于结果不确定，不自动重放导入。Core/Spec 的回执缺口没有以 Room/SQL 绕过。旧格式只通过新 Core legacy API，不启用旧引擎。
- **测试替换**：删除九个已依赖旧组件 API 且漂移的 obsolete instrumentation 文件，保留并现代化四个纯 UI/context 测试，新增 Core accounts UI 和路由场景。纯 UI 测试使用构造状态，通过 `IsolatedUiTestRunner` 强制普通 `android.app.Application` 并断言应用类，不调用 HootbookApplication/Hilt 或真实 MainActivity，因此不自动开启旧同步/用户 Core 账本。该 runner 不等同独立 applicationId，实际设备仍应使用专用测试设备/空数据；standard 的 Firebase provider 初始化不作为线上同步验证。旧生产路径暂存不代表新 UI 继续兼容旧接口。
- **CI 门槛**：Core 单测 wildcard 包含 settings/currencies/backup/navigation 和宿主目录；两 flavor 的 Android instrumentation Kotlin 编译及测试 APK assemble 均显式加入测试工作流。这是编译/打包门槛，不是 connected device 测试执行证明。

### 文件恢复仍属部分接入

- **提交回执缺口**：Core 没有可持久查询的 import receipt。导入中断或提交后报告落盘前退出，会显示 UNCERTAIN，并阻止自动重放；当前没有自动解锁入口，需保留源副本/日志并人工核对，不宣称无感完整恢复。
- **R1 已修**：仅精确的预检错误白名单允许记为 FAILED（文件格式/版本、口令格式/缺失、旧导入非空、新备份 schema-too-new）；Storage(IO)、其它未知 typed 错误、取消均不能证明未提交，持久记为 UNCERTAIN。测试覆盖 Core 已写 commit marker 后抛 IO，第二次恢复不再次调用导入命令。
- **平台偏好**：preferences 只供审阅与另存，不自动应用旧业务设置/ID/凭据。profile/merchant_icons 资产仅保存并可带入后续导出，尚未接入新 UI 显示消费者。保留资产不等于平台体验已完整恢复。

### 第四批实际验证结果（整批回滚待最终确认）

- 集成阶段第二轮 App 定向单测实际 **196 项**，公共模块单测强制重跑实际 **14 项**，合计 **210 项全部通过，0 failure/error/skipped**，以 XML 计数。第一轮 191 项原始日志保留；R1 修正后新增 5 项 commit/preflight 断点验证。生成接口 fake/JVM 状态机测试不代表设备 native/UI 验收。
- standard/fdroid Core-only Kotlin 编译通过；独立审核又完成默认入口两 flavor 编译并核对 `CORE_ONLY=false`。两 flavor Core-only slim debug R8 主 APK、App instrumentation Kotlin 编译及测试 APK assemble 均通过。删除九个 obsolete 旧 UI 测试，保留并现代化四个纯 UI/context 测试，新 Core 场景及 runner 断言合计 **15 项设备 case，仅编译/打包，未运行**；上一批旧 UI 编译阻塞已实际清除。
- 主 APK 的 Core/JNA ABI 覆盖、pinned native 字节、当前 Android 64 位 ELF 16 KB PT_LOAD、`zipalign -P 16`、生成类型/callback DEX 与新页面 R8 mapping 检查通过。standard 主 APK 为 arm64-v8a，fdroid 沿用已有配置包含 arm64-v8a 与 armeabi-v7a；主/仪器 APK debug 签名、instrumentation runner 与新测试 DEX 检查通过。公共 AAR/测试 APK 已归档与检查；JNA AAR 所含旧 MIPS/armeabi 等历史载荷不构成这些 ABI 的设备支持承诺，也不扩大当前 Android 64 位对齐结论。
- Locale 的冷启动/活会话、失败补偿/重试、SDK33 OS per-app 权威语言已有测试和接线；dependencyInsight 证实真实 resolved `androidx.core:core:1.17.0`，公开 LocaleManagerCompat API 可用，无依赖升级。
- instrumentation 用普通 Application 阻止生产 Application/Hilt/账本启动，但不改变目标 uid/applicationId，也不移除 manifest providers。connected 测试应在专用空数据设备上执行；本轮未运行设备 native/UI/Keystore/性能场景，未在云 CI 执行工作流。
- **仍未生产切换**：默认入口继续旧路径，Core-only 两 flavor 仍离线，Firestore v2 未接；旧引擎/Room/Drive/WebDAV 删除及其它业务/系统入口仍在后续范围。本批构建成功不等于完整迁移或发行验收完成。

共享接线 before/hash、修改快照、diff、精确命令及输出/退出状态、rollback 位于 `build/core-settings-backup-integration-audit`；独立默认/R8/打包审核与归档产物位于 `build/core-settings-backup-review-audit`；旧 UI 测试清理位于 `build/core-ui-test-cleanup-audit`。本批全量路径的真实 rollback/baseline/reapply/modified 往返由独立审核继续执行，在最终审计确认前保持 pending，不预先宣称已完成。
