# Hootbook Android → HootKit Core 迁移结果

> 历史迁移验收：保留当时双 flavor 的真实测试、APK SHA 与设备事实。
> 当前仅 standard；本次移除 flavor 的增量与 fresh 验证见 [standard-only.md](standard-only.md)。
> 下列旧双 flavor 任务、计数及产物属于上轮验收，不是当前 variant 或本次重测。

## 1. 交付结论与范围

Android App 已从旧 Room/Kotlin 账本业务迁移为默认、唯一 Rust Core 路径：所有正常 flavor/buildType 通过同一 application-owned `CoreSession` 使用生成的 Core 类型及公开 API。Android 保留 Compose、导航、展示、平台偏好、认证、文件选择/分享、Keystore/HTTP 回调和系统入口，不再维护第二套账本、金额/预算/汇率规则或兼容 Long ID Repository。

本任务只修改 Android App 仓库；Core/Spec 及 pin 保持原值。现有 Core 接口能够表达的既有 App 功能已接入，API 缺口单列，不用近似 UI 或 Kotlin 业务重算补齐。**源码实现、编译/JVM、真实 UI、native/platform/backend fixture 和线上验收是不同证据层，不等同“全量功能与所有组合已实跑”。**

- Core：`9ca2916045e5d5145776ec75f2ea723e022f0a61`。
- Spec：`e662bb9c876c52d4672e80da5977bc7e32c664f9`。
- `core-bindings/core.lock.json` 原 pin 未变；最终只读 hash/HEAD/clean 记录以聚合 verification 为准。
- 未自动读取、导出或删除用户旧 Room 库及原备份；旧格式迁移只由用户选择文件、调用 Core `legacy()` 导入。

## 2. 已迁移与保留的能力

| 领域 | 当前唯一路径与能力 | 验收层级 |
|---|---|---|
| 账户/口袋/信用卡/锚点 | Core 字符串 ID、余额、校准、卡账期、单账户/口袋 `balanceAt` / `balanceSeries`、图标样式与目录 | 源码 + JVM；代表性 Main Wallet100/冷启动持久化及 native 账本 fixture |
| 交易/分类 | Core 增改查、分页/现有筛选、转账、批量、软删/撤销/永久清理、分类/子分类；CSV 遍历全部页；附件私有复制和窄 FileProvider | 源码 + JVM；UI 表单与直接 native 命令分开列证据 |
| 预算/计划/借贷 | 历史继续超过24周期，游标使用 Core 返回的 start；历史分类 `get(id, exactPeriod.start, displayCurrency)`；订阅付款/模板预填/借贷关联和结清 | 源码 + JVM/native fixture；不把 ledger 普通筛选当准确预算交易清单 |
| 币种/首页/分析 | Core 设置、汇率、`Currency.convert`、汇总/统计；`complete` / 缺币种状态可见；Android 仅格式化 | 源码 + JVM/native；Home 已实际使用 convert，不泛称换算缺失 |
| AI | Core 当前全部 AI API、文件/图片/PDF 预处理、会话/取消、工具提案审阅/批准/保存/撤销；秘密不进入持久草稿 | 源码 + JVM；受控 HTTPS native/Main 只实跑 add_transactions + finish，不冒8工具全覆盖 |
| 券商 | 当前 Core provider/连接/最新快照/链接/刷新/断开；UI 输入校验及错误/取消 | 源码 + JVM/受控故障；未使用真实券商凭据 |
| 文件备份/旧格式导入 | Core 新备份加密导出、检查、Merge / ReplaceAll；legacy inspect/import；Android SAF、确认、安全副本、平台偏好与图片实际消费者 | native/平台故障/真实 SAF / 进程 kill 按最终矩阵区分；uncertain 保留源不重放 |
| 同步 | 当前仅 standard Firebase v2/Auth/生命周期/WorkManager 回调；未配置或未授权时正常离线 | JVM + demo SDK backend；不是两 Core 同步或真实 Google OAuth |
| Android 平台 | profile/头像/banner/merchant 图片、主题/字体/语言/icon/home widgets、AppLock、通知/权限/Boot/Widget/快捷方式/3 QS tiles、分享/旋转/IME | 源码 + JVM；真实代表性设备路径与未测组合单列 |
| 纯展示与非账本功能 | 原主题、图标/品牌素材、About/licenses/FAQ/crash、Ktor updater/AppStore、PdfBox 文档准备 | 保留真实使用依赖，不混为旧引擎残留 |

逐领域源码矩阵：`docs/core-final-function-matrix.md`。历史接入记录：`docs/core-integration.md`；结构边界：`app_adapt.md`。

## 3. 已移除的旧实现

移除 App-owned Room 数据库/DAO/Entity/schema/KSP 配置及直接 Room 依赖、旧账本业务 Repository/UseCase/余额规则/ID 桥接、旧汇率业务服务、旧 AI broker/HTTP/chat/tools、旧券商 parser/store/业务计算、旧同步引擎/outbox/schema/crypto/backend、旧备份导入导出/encryption/cloud 管理、Google Drive/WebDAV provider/页面/凭据/专属依赖、Cashew 旧映射器和仅依赖已删除引擎的测试。替换为 Core adapter/生成接口测试，不保留双引擎生产入口。

保留 WorkManager **内部传递 Room** 是其实现依赖，不是 App-owned 账本；保留纯主题/资源/Ktor update/AppStore/PdfBox 及公共 Android 库是实际平台功能。benchmark SeedActivity 已改为等待 Core 异步打开并调用 Core 命令，性能 CI 不再拼接旧 Room main/flavor 到新依赖图；编译 CI 接线不等于跑过性能旅程。

## 4. 真实发现并修复的 App 集成问题

1. **JNI 包装名**：生成 loader 请求 `uniffi_hootkit_core`，原 APK 包装名不匹配。App 构建将 pinned Rust 输出复制/strip 为 `libuniffi_hootkit_core.so`，不改 Core crate、UDL、pin 或生成 Kotlin。旧坏 APK 作为负例保留，普通新 APK 实际加载/冷启动通过。
2. **instrumentation runner**：manifest 中 runner 名称/DEX 引用不代表存在类定义。公共模块补 AndroidTest-only runner 1.7.0，静态检查解析实际 DEX class definitions；修正后的 native smoke/Keystore 2/2。
3. **生产 tab 导航**：onboarding 节点移除后 Profile→Home 留在 Profile。修复生产 tab anchoring，增加实际 NavController 回归；真实相同物理 taps 前后失败/成功都保留。
4. **AI cancel/Main 重入卡死**：旧真实 Main 取消冻结约4分钟，trace 保留在 `build/core-final-device-audit/rawlogs/ai-cancel-hang-trace-root.txt`。cancel 同步 wake 与 UniFFI continuation 在 Main.immediate 下重入 poll 是已观察的 App 集成调度敏感点。App-only always-dispatch native IO 与单 owner lifetime 管理先取消 continuation/等待 run unwind，再后台 cancel/destroy；成功只向 Main 转移 review owner，clear 后独立 cleanup，save/undo 原子命令继续 NonCancellable。修复后真实 native503/slowCancel 与批准0→1→undo0场景2/2；不宣称 Core 已修改或独立 cargo 复现。

全部失败/首次崩溃/驱动问题均保留，后续成功不覆盖历史。

## 5. 最终实际验证结果

最新 source 阶段实际 forced、无筛选、默认路径单位测试：

| 范围 | Fresh XML | 实际 tests | Failure / error / skipped | Exit |
|---|---:|---:|---|---:|
| standard 全 App | 68 | 493 | 0 / 0 / 0 | 0 |
| fdroid 全 App | 67 | 489 | 0 / 0 / 0 | 0 |
| 公共 core-platform | 3 | 14 | 0 / 0 / 0 | 0 |

跨 flavor 实际执行 **996**，按 XML `classname#method` 去重 **507**；Fdroid489 严格为 Standard493 子集，Standard 多4个 Firebase SDK 边界测试（无真实账号/网络）。历史486/482/14=982执行/500 unique 和 AI34/34 子集不是最新总数。来源：`build/core-final-integration-audit/postcancel-fresh-unit-case-identity-summary.json` 与 `runs/postcancel-{standard-all-tests-1,fdroid-all-tests-1,public-platform-tests-1}/record.json`。

最新8个生产 variant（两 flavor × release/preview/benchmark/profiling）及 benchmark module forced Kotlin 编译 exit0；普通双 debug minify R8 和两 release R8 实际执行 exit0。普通新 APK `CORE_ACCEPTANCE=false` / `DEBUG=false` / 无 `CORE_ONLY`，cleartext/allowBackup=false。严格 loader/native pin bytes/JNA/64-bit ELF16KB/ZIP alignment/Debug certificate/旧 App engine DEX 缺席与 F-Droid Firebase/Google/GMS-free 检查通过。当前四ABI AAR/native AndroidTest APK包装与两flavor候选AndroidTest Kotlin已强制编译；verify.py原生字节/ELF/JNA/备份排除、runner-apk真实AndroidJUnitRunner class definition/CRC检查exit0。记录：`runs/postcancel-native-fourabi-and-test-packaging-1` 及 `postcancel-native-artifact-index.json`。所有 R8/ABI 检查均不替代设备加载、官方发行签名或性能。

最终27领域**代表性证据矩阵**：`build/core-final-device-audit/coverage-27.{md,json}`。31个 parsed run records 中，36个 completed unique methods 各选择到至少一次 literal PASS；37个 ever-started 包括 intentional kill harness。共85个 completed named executions：**42 PASS、43 FAIL、0 skipped**，另1个 framework initialization error；3个 started unfinished（SAF host abort、旧 AI hang、intentional process kill）不计PASS。不是同一 APK 的 fresh36-method全通过轮，也不称27领域所有操作/组合都完成。

修复后最新业务8/8 literal PASS 绑定真实 Main `b61abf35363f92817fe34285f6a67cc08d23e6f4b3581f3bf166367be46b9fd4` / test `220dae79a210daaec619a44e6cf4399eb7613a459cc317fcccda2218645b6f9b`：分类/子分类保护与删除、Expense all-time future 分析、14菜单 route、订阅排期/mark paid/用户确认付款、信用卡/口袋/校准/历史/归档、native seed206后 UI 筛选/全选/批量1.25/CSV206/Next page、实际 DocumentsUI 附件和精确商户保存、预算80/分类限额10/LoadEarlier>24/精确 Core 历史周期。**预算 Hide/Show on Home 不证明暂停金融计提；native seed不是UI创建206笔；记录过 host IME-dismiss/card locator assist，不冒称无人辅助 driver 全通过。** 新旧 APK/source stage 与所有失败均保留。详见 `journeys/final-business-batch.md`。

### 设备环境与证据层

手机/平板为本任务自建隔离 AVD，API36、arm64-v8a，guest page size4096。普通 F-Droid/Standard minified 非 `.coreacceptance` 包已真实冷启动、完成 onboarding、保存 Wallet100 并跨冷启动/普通渠道安装保留；验收测试另用 `.coreacceptance` 安装，不替代普通 APK。

平板代表性 UI/AppLock 已完成：21条UI证据断言+1条自有模拟器关机断言、14张即刻视觉检查 PNG；**不是21条JUnit**。真实 device PIN enable/cancel/cold gate/后台65s（timeout1min）/SECURE、font2/横竖屏/IME、SAF cancel/锁返回恢复草稿、中文/declared Arabic RTL、主题/icon/home widget order/visibility。Arabic 有新文案 English fallback，不称全翻译；未穷举所有样式、字体、语言、API/设备种类，未验证 biometric hardware 或自发 OS process death 草稿。证据：`build/core-final-device-audit/tablet-ui-lock/{RESULTS.md,verification.json}`。

AI fixture 在真实 Main/Hilt/同一 CoreSession/现有 singleton transport 内请求声明8工具，但服务实际只发2种调用（add_transactions、finish）。TEST-only CA 暂换现有 client 的信任，保留原 hostnameVerifier，finally 恢复 original client identity；生产 CA/device trust/pin 字节不变。不是线上 AI 计费服务或生产 system CA 验收。

Firebase fixture 用 demo emulator 与真实 SDK anonymousAuth；两个 App v2 backend wrappers 共用同一 client/auth，验证事务/服务器读取/crossUID Unauthorized/delete。不是两独立 Core 同步引擎、Google OAuth 或生产 Firebase，亦不替代 Core #62/#63/#70 验证。

平台故障 fixture 在真实 Core 返回后注入平台 apply 失败，可以证明 `CORE_COMMITTED` 重试只处理平台、不再次 native import；合成 `IMPORTING` journal 只证明 App uncertain 策略。另有真实受控 process kill：native import 已返回（added0），AndroidTest 暂停向生产 workflow 交付；host 只停止自有隔离 app PID，重开 g case 1/1 PASS，production journal 为 UNCERTAIN、同 sourceSHA/源文件保留且重放拒绝。真实备份页可 SaveOriginal，经 DocumentsUI 保存91400B并校验原 SHA。intentional kill 的 f harness 是未完成，不算PASS；这不是自发 Rust commit 边界 kill 或 durable receipt 验证。见 `journeys/processkill-and-external-share.md`。

### 未测与发布边界

未使用/请求真实 Google、生产 Firebase、AI 计费或券商账户凭据；未运行云 GitHub CI、物理设备性能、所有线上服务/双 Core 同步、所有 UI 参数组合。16KB ELF/PT_LOAD、APK zipalign 是静态打包证据；未实跑16KB guest。普通 APK 为本地 Debug 签名 minified 产物，不是官方 release 签名、商店发行或生产上线认证。

## 6. 当前 Core 公共 API 缺口与明确未补能力

- 精确计入预算的交易 ID 分页（不是普通 ledger 查询）。
- 全局子分类/金额范围/多币种高级筛选、Smallest/Merchant 排序；当前 SortOrder 仅 Newest/Oldest/Largest。
- merchant 金融排行、金融日均、旧投资/借贷特殊合计等专门统计，以及精确历史估值的全局净资产 API；首页按用户明确批准，读取 Core balanceSeries/convert 做展示用加减，提供标注“按当前汇率估算”的走势，证券沿用当前估值，不把它声称为历史当天的真实总资产。
- Cashew 第三方 SQLite/CSV 专门导入；Core legacy 是已有旧备份格式。
- 原子清账但保留分类/汇率/设置；不删除数据库或伪造空备份。
- durable import operation receipt/query/resume、uncertain 自动恢复；原源/安全副本/导出保持，uncertain 不自动重放。

**不是缺口**：预算>24历史分页/历史分类精确period、Currency.convert及Home换算、平台偏好及图片恢复均已实现。完整 analytics 的批量一致币种快照/专门统计按 API 实际边界，不泛称“换币未支持”。

## 7. Core/Spec 已知问题（只读依据，未改共享核心）

`build/core-final-integration-audit/core-spec-known-limit-evidence.json` 保存以下主文件/行/SHA；本任务未运行 cargo 或 Core 性能基准复现它们。

| 项目 | 当前已记录语义与 App 边界 | 主依据 |
|---|---|---|
| #62 | 首次同步内置分类 `base_row` 空，后续单列可能按双方修改处理；不宣称无丢修改保证 | Core `docs/spec-questions.md:50`、`docs/SYNC-MODEL.md:132–137`、`tests/sync_merge.rs:210` |
| #63 | 上传确认丢失后改回原值，可能取回云端已上传值而无冲突 | 同上 `:61` / `:139–145`、`tests/sync_ack.rs:108` |
| #70 | `SYNCED_UID` 推拉结束后才写；部分首次同步中断再换账号仍有归属边界 | Core questions `:53`、engine `:998–1025`；Spec `06-sync.md` §16.1 |
| 导入回执 | Backup 仅 export/inspect/import，summary无 operation receipt/query；legacy importedAt 不是特定源提交证明 | Core UDL `:1205–1235,:1476–1480`；Spec `07-backup.md:81–83` |

App 每安装固定UID/缺witness闭门是明确产品限制，不是修好 Core #70。数据库单事务失败保持不变，Spec先解压的附件/平台文件不删除；不宣称账本半提交。prefs/assets 应由平台消费（Spec backup `:16–18` / API backup `:44–51`），现 App 已导出/恢复；“只审阅偏好/只保留图片”不是Spec限制。

Core 仓库性能文档的预算问题仅为其历史测量自述；本任务没有当前 Android 物理性能结论。

## 8. 交付路径、回滚与数据保护

普通非 Acceptance 的本地 minified APK（version `2.1.63-debug+c37b280`；Android Debug certificate）：

| Flavor / ABI | 不可变路径 | Bytes / SHA256 |
|---|---|---|
| standard / arm64 | `/Users/bytedance/Documents/Hootkit/hootbook-android/build/core-final-integration-audit/artifacts/postcancel-ordinary/standard-core-minified.apk` | 16960262 / `25977fd941c1b9257c4acfb8297e6192a97b4c7830c8bfe47244e78ec8d7f0f2` |
| fdroid / arm64 + v7 | `/Users/bytedance/Documents/Hootkit/hootbook-android/build/core-final-integration-audit/artifacts/postcancel-ordinary/fdroid-core-minified.apk` | 19335155 / `d09375dfa4c723c4e4f8315fc802f991a91968ce1ea4f3d00368a8b323470866` |

来源：同目录 `immutable-apk-index.json`；不引用旧坏库名 APK 或以 `.coreacceptance` 代替普通交付。最终这两份普通非Acceptance APK已在自有 API36 ARM64 手机断WiFi/mobile、无clear-data/无provider请求下实际install -r，并回读guestbase.apk SHA精确匹配：冷启动Wallet100→Profile→Home正确→Settings空配置AI→force-stop重开Wallet100。两flavor共10条layoutchecks/6次Home持久化观察，089/101/111PNG实际visual；AI099/110因SECURE黑屏只用layout验证。不是10条JUnit或各flavor全部业务重新测。证据：`build/core-final-device-audit/journeys/final-ordinary-flavors.md` 与 `metadata/final-ordinary-device-verification.json`。

聚合四角色及验证入口（执行范围、baseline/modified literal 命令与往返结果以 verification.json 为准，本文不替代验证记录）：

| 角色 | 路径 |
|---|---|
| Modified artifact | `/Users/bytedance/Documents/Hootkit/hootbook-android/build/core-final-integration-audit/modified.tar.gz` |
| Patch | `/Users/bytedance/Documents/Hootkit/hootbook-android/build/core-final-integration-audit/changes.patch` |
| Verification | `/Users/bytedance/Documents/Hootkit/hootbook-android/build/core-final-integration-audit/verification.json` |
| Runnable rollback | `/Users/bytedance/Documents/Hootkit/hootbook-android/build/core-final-integration-audit/rollback.py` |

回滚以逐文件目标前稀疏原件为准，而不是 git HEAD reset；已有 owner/foundation/user 文件保留。四现有文档 whole-goal baseline 不被本阶段 `runtime-docs-correction` baseline 覆盖，新增 result.md 目标前明确 absent。文档自身只在私有 fixture 验证 apply→hash→rollback→hash→reapply；全任务 workspace 往返由 Root/独立 verifier 管理，执行结果以聚合 verification 为依据。

设备只使用自有隔离 AVD/fixture 数据；不接触用户既有设备账本。平板已按批准关机并保留 userdata/fixturePIN；手机/本地服务最终清理状态见 device owner 报告，不自行声称删除或关闭。
