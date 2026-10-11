# Hootbook Android 接入 HootKit Core：结构设计

## 1. 目标与设计基线

将 Android App 从「Room + Kotlin 账本业务」迁移为「Compose + ViewModel + Rust core」。页面按 core 的接口和类型调整，移除为旧 UI 保留的 Entity、Repository 与 ID 兼容层。

本文件是结构约定。当前App默认唯一Core、旧App-owned Room/业务引擎及专属云备份源码/直接依赖已删除；现Core API支持页面/平台入口已接。最新实际编译/JVM/R8与代表性UI/native证据已记录，未测组合/线上服务和API缺口显式保留；不等同所有功能组合实跑。最终结果见 `docs/core-migration-result.md`，历史记录及源码矩阵见 `docs/core-integration.md` / `docs/core-final-function-matrix.md`。

### 已确认的范围

- 最终 App 只使用 Rust core，直接删除旧引擎、Room、旧业务 Repository 及 ID 兼容层；不保留双引擎或 legacy-export 组件。
- 删除 Google Drive / WebDAV provider、云备份页面及专属凭据、依赖；保留 core 文件备份和 Android 文件选择 / 保存 / 分享。
- standard 的 Firebase 账本同步单独保留；fdroid 的 `auth` / `remote` 为空。
- 旧格式备份通过新 core 的 `legacy()` 导入；新版不自动读取或导出旧 Room 库。

- App 检查基线：`main@c37b280f`。
- Core 检查基线：`9ca2916045e5d5145776ec75f2ea723e022f0a61`，位于 `claude/brave-archimedes-6r4h7e`。
- 规格基线：1.0，`e662bb9c876c52d4672e80da5977bc7e32c664f9`；API、类型及行为以 core 的 `src/hootkit_core.udl` 和 `spec/` 为准。
- 保留 applicationId `io.github.hootkit_dev.hootbook`、Compose、Hilt，以及 `standard` / `fdroid` 两个 flavor。
- 接入过程中新增的 core 缺陷或接口需求交回 core / spec 仓库处理，保持共享核心与平台代码的边界。

## 2. 整体结构与依赖方向

```text
:app
  Compose 页面、导航、ViewModel、UI 偏好、系统集成
       │                    │
       │ 直接使用 core API   │ 会话与平台能力
       ▼                    ▼
:core-bindings ◀────── :core-platform
  UniFFI Kotlin 类型     CoreSession / ChangeBus
  Rust native libraries  Keystore / HTTP / 日志 / 同步适配
       │
       ▼
  HootKitCore：业务规则、查询、SQLite、同步与数据导入导出
```

### 模块职责

| 模块 | 职责 |
| --- | --- |
| `:core-bindings` | Android library；封装同一 core 提交生成的 Kotlin 绑定和各 ABI `.so`；声明 JNA AAR、协程依赖及 native / R8 打包要求 |
| `:core-platform` | Android library；核心的运行宿主：异步初始化、变化通知、平台回调、生命周期和后台任务接线 |
| `:app` | 页面与 ViewModel、导航、主题、应用锁、权限和文件选择、分享入口、系统通知、widget、快捷方式等 |

Firebase 实现在 `standard` 专属代码或专属适配模块中。公共模块及 F-Droid 依赖图保持 Firebase-free。

ViewModel 使用注入的 `CoreSession` 取得 core，直接调用 `accounts()`、`ledger()` 等公开服务。移除旧 Repository 转发门面以及 Kotlin 中重复的记账规则。

## 3. 各层的数据与行为归属

### core 是账本数据与业务规则的唯一来源

以下内容交给 core：账户与口袋、余额与锚点、交易增删改查和校验、分类、预算、订阅、借贷、模板、汇率与主货币、AI 业务设置与会话、券商连接与快照、同步、账本备份与恢复。

Android 仅通过公开 API 访问 core 的账本。core 独占 `hootkit.sqlite`，其表结构、事务、触发器和业务设置均由 core 管理。

### Android 保留平台及展示职责

- Compose 渲染、输入框草稿、加载与错误状态、筛选和分页条件、选中项、弹窗、导航。
- 金额和日期显示、资源与图标解析；core 决定金额和日期的业务语义。
- 主题、布局、应用锁、提醒等 Android 专属偏好。
- 系统文件选择 / 分享、URI 访问、权限、通知、widget / shortcut 展示。
- Android 文件选择、保存和分享；备份内容和加密由 core 生成或读取。不再保留 Google Drive / WebDAV 集成。

设置逐项归属：主货币、默认账户、汇率、AI 配置等使用 core API；语言选择作为 UI 偏好保存，打开 core 时传入当前语言，切换后调用 `settings().setLocale()`。

### 类型直接使用生成的绑定

- `UiState` 直接包含 core 返回的记录和列表，额外字段限于页面状态。
- 同步实体的字符串 ID 贯穿查询、导航、筛选与保存；取消旧 `Long` ID 的假兼容映射。
- 冲突编号、券商连接编号等本地整数 ID 保持 UDL 对应类型，逐项区分。
- 金额使用绑定配置映射的 `BigDecimal`；日期使用 core 类型，并在显示 / picker 边界做转换。
- 格式化、资源解析是展示函数，保持与余额计算、汇率换算和交易规则的职责分离。

## 4. ViewModel、页面状态与变化通知

ViewModel 属于 UI 层，是屏幕级状态持有者。它负责查询和命令编排，Compose 负责显示状态与发出用户操作。

```text
用户操作 → ViewModel → core 命令
                         │ 事务提交
                         ▼
Compose ← StateFlow ← ViewModel 查询 ← ChangeBus / ChangeListener
```

- 一个页面或导航目的地使用一个 ViewModel；复用小组件使用普通 UI state holder。
- `UiState` 包含 core 数据、loading、错误及筛选 / 分页等页面状态。
- 查询使用 suspend API；UI 生命周期感知地订阅 `StateFlow`。
- 命令成功后，以 core 返回值和重新查询结果更新页面；数据一致性由 core 负责。
- 最新筛选条件优先：取消过期查询或校验请求版本，避免旧结果覆盖新条件。
- 用户提交的写命令单独管理，和可取消的刷新查询分开；避免快速连点导致重复保存。

### ChangeBus

- 在启动 core 之前创建并传入 `ChangeListener`。
- 回调仅记录失效信息并快速返回，查询在其他协程执行。
- 以每张表的递增 revision 或等价可靠失效状态驱动刷新；通知合并后仍保留所有受影响表。
- 页面订阅后执行首次查询；重新进入页面也读取当前数据。使用失效状态避免缓冲区满或暂时无订阅者时漏掉刷新。
- 可以短窗口合并刷新，例如 50 ms；首次显示和保存后的反馈保持及时。
- 先采用按表失效，再优化 `account_ids` 定向刷新；全局设置变化仍刷新相关合计。

例：首页依赖账户、口袋、交易、锚点、汇率、设置及涉及的券商快照；同步页依赖 `SyncState`。依赖表按实际查询语义登记。

## 5. CoreSession 与启动流程

`CoreSession` 是按进程唯一的核心宿主，管理异步 `openCore()`、就绪状态和显式关闭；ViewModel 获取就绪 core，而不是在 Hilt Provider 中阻塞初始化。

建议启动状态：

```text
Starting → Opening → Ready → Closing → Closed
              │
            Failed → 显式重试 Opening
```

- 明确区分打开失败、备份导入失败与页面查询失败，给出对应的重试 / 恢复入口。导入由用户发起，不属于启动自动迁移。
- 新库位置采用 `context.getDatabasePath("hootkit.sqlite")`，与旧 Room 库分开。
- `CoreConfig` 包含独立的备份、附件目录和当前 locale；正式运行 `fixedNow = null`。
- `:crash` 等独立进程跳过 core 初始化；widget、后台任务等按明确的进程模型共享宿主。
- Activity 重建复用会话；App 进入后台继续保留核心，由同步生命周期接口处理后台行为。
- 测试、显式维护或替换实例时先 await `shutdown()`；单纯释放绑定对象与关闭数据库分开处理。
- 原有用户 applicationId 和发行签名保持稳定。

## 6. Android 平台适配

| 接口 / 能力 | 设计职责 |
| --- | --- |
| `ChangeListener` | ChangeBus，驱动页面失效刷新 |
| `Logger` | Logcat；记录诊断标识与计数，敏感请求、密钥和完整交易明细保持脱敏 |
| `SecureStore` | Keystore 保护的设备绑定存储；线程安全；同步方法及时返回；写入具有明确持久化语义；已迁移而解密失败的值按规格视为不存在 |
| `HttpTransport` | 按平台规格实现 HTTPS、整体超时、重复 header、重定向开关、流式大小上限和错误分类；4xx / 5xx 作为正常 HTTP 响应返回 |
| `AuthProvider` | standard 的登录账号快照；登录 UI 仍由 Android 提供 |
| `RemoteStore` | standard 的 Firestore v2 适配，严格实现规格 04 / 06 |

所有回调按任意线程、可能并发的条件设计。不带 Throws 的回调内部捕获异常；带 Throws 的回调按 `PlatformError` 分类。UI、Activity、Context 的生命周期由平台层处理。

删除旧云备份凭据存储及明文降级分支。新 SecureStore 使用 Keystore，不做明文降级。数据库及 `-wal` / `-shm` 显式排除系统云备份与设备迁移；core 附件、备份及安全存储置于 `noBackupFilesDir/hootkit-core`。

## 7. 同步、认证与后台任务

保留 Firebase / Credential Manager 登录界面，替换旧 `SyncEngine`、同步加密业务和旧 outbox 管理。Android 平台仅提供远端存储及生命周期事件。

- 使用 `users/{uid}/v2_records`、`v2_meta`；旧 `records/meta` 仅用于规格定义的旧协议检测与显式处理。
- `write` 在一个 Firestore 事务里检查 crypto 和 head，再整批写入记录及 head；将 `Stale`、`CryptoChanged` 原样按平台错误回传。
- 拉取及 crypto 读取使用服务器数据；排序和游标包含时间及文档 ID。
- 前台仅监听 head，按规格忽略本设备写入和未由服务器确认的快照；监听回调驱动 `remoteChanged()`。
- 前台进入、后台进入、手动同步和 WorkManager 重试使用 core 对应入口；利用 `appBackgrounded()` 的返回值、`retryAfterMs` 与错误类型安排任务。
- F-Droid 创建 core 时 `auth` / `remote` 为空，按能力展示界面；其构建保持 Firebase-free。

### 账号隔离是平台验收重点

远端操作绑定确定的 uid；在途请求的目标账户保持不变。避免在各个请求中动态拼接新 `FirebaseAuth.currentUser` 的 uid。

认证变化期间使用协调入口和请求闸门：停止旧监听，限制新的远端请求，更新认证快照并 await `authChanged()`，完成清理后再绑定并放行新账户。覆盖「正在上传 / 拉取时切账号」的并发测试。

当前 `setPaused(true)` 只设置暂停状态，并非等待当前同步轮完成的屏障；实现时按 core 的实际同步与认证语义验证这套交接流程。

## 8. 旧格式备份导入与新备份

旧引擎及 Room 直接退出。新版只接受用户选择的备份文件，不自动读取或导出旧库。

```text
用户在旧版导出备份 / 已有旧备份
  → 新版选择文件并复制到私有暂存目录
  → core.legacy().inspectBackup / importBackup
  → 核对报告、口袋当前余额、附件及平台偏好
  → 显示导入结果，继续使用新 core
```

- 用已有 App 的真实备份验证兼容性，保留 `databaseVersion = 57` 的历史样本；不为新版保留旧导出器。
- 删除 Room 运行依赖、DAO、Entity、旧种子及同步触发器；所有新页面与写入直接进入 core。
- 导入状态与 `legacy().importedAt()` / 导入结果协调；覆盖导入提交与平台处理之间的进程中断，避免重复导入。
- legacy 导入要求目标账本为空；已有 core 数据时显示明确提示，新格式恢复使用 core 的 Merge / ReplaceAll。
- 核对导入和跳过数量、币种重命名、当前口袋余额及资产文件；历史余额变化按 legacy 规格解释。
- 根据 `LegacyImportSummary` 处理 `preferencesJson` 和 `platformFiles`，避免继续保留引用旧 ID 的偏好。
- 保留用户原始备份；新账本产生新数据后，恢复以 core 备份机制为准，不回退旧引擎。
- 旧备份不包含快捷模板；其兼容能力需由core/spec接口表达，不添加 Android 旧 ID 兼容层。
- 新数据的备份导出 / 检查 / Merge / ReplaceAll 由 core 完成；Android 负责目的地、授权、上传下载及平台文件。

## 9. Native 构建、版本与测试边界

- core 提交、规格提交、UniFFI 0.29.5、Rust 1.97.0、NDK 28.2.13676358 固定在 `core-bindings/core.lock.json`；绑定及 native 构建分别生成来源和哈希记录。
- 使用 core 的 `uniffi.toml`：包名 `dev.hootkit.core`、不可变记录、Android 配置和 Decimal 映射。
- 为 Android 的 minSdk 26 构建 native libraries，按 App 各 flavor / variant 的 ABI 打包。
- 保留四个 ABI，构建脚本补齐 x86；确保 split / universal / bundle 中每个声明 ABI 都包含库。
- 检查 Rust 库和 JNA native library 的 16 KB ELF / APK 兼容性；选择已有相关修复的 JNA AAR，而不是照搬 core JVM 冒烟测试使用的版本。
- Kotlin JVM 测试、真实 Android 加载测试和 R8 构建分开验证；生成的 Android 绑定按其运行要求使用 Robolectric、Android instrumentation 或专门的 JVM 测试配置。
- 更新 CI：standard 与 fdroid 构建、接口加载及核心往返测试、R8 native / callback 测试、迁移与平台适配测试、设备端性能测试。

## 10. 实施顺序与交付门槛

| 阶段 | 范围 | 验收重点 |
| --- | --- | --- |
| A. 运行宿主 | bindings、native 打包、四个必需回调、CoreSession | Android 真机 / 模拟器成功打开 core、查询版本、关闭重开；R8 和 ABI 验证 |
| B. 核心账本页面 | 账户、交易、分类、首页与页面刷新 | 直接使用 core 类型和 API；增删改查及跨页面刷新；移除旧余额计算 / 账本写入路径 |
| C. 文件备份与导入 | core 备份、legacy 备份导入、平台偏好与文件处理 | 真实备份往返、余额和数量核对；故障恢复；不读取旧 Room 库 |
| D. 其余业务 | 预算、订阅、借贷、模板、汇率、备份、AI、券商 | 逐页面移交到 core；平台能力和业务能力边界清楚 |
| E. 同步及发布 | Firestore v2、认证交接、后台重试、最终清理 | 两 flavor、并发 / 失败注入、跨设备验证；删除退出使用的旧业务与依赖；设备性能验收 |

阶段划分用于独立验证与评审，生产版本的切换应在所有账本页面完成迁移后统一进行，避免同一用户账本同时由旧、新引擎维护。早期阶段使用隔离的测试数据库 / 测试入口验证。

### 实现 agent 必须保留的验收项

- ViewModel 使用 core API 和生成的业务类型；账本页与旧 Room Entity / DAO 解耦。
- 页面仅维护 UI 状态；余额、预算、交易校验等规则在 core。
- 页面旋转、返回前台、通知合并和快速筛选后的状态正确。
- SecureStore、HTTP、回调线程与异常、跨账号同步、备份排除和 legacy 恢复具备测试。
- 使用实际 App 备份和设备端测试验收；core 本机 Rust 测试通过与 Android App 接入成功分别确认。
- pinned core 仍有已记录的规格问题：#62、#63、#70（首次同步中断后换账号）等，以及同步性能预算未全部达标。实现与发布前显式评估相关流程和风险，尤其账号隔离；通过规格修订、核心修复或明确的产品限制处理，保持结果可见。

## 参考

- `../hootkit-core/src/hootkit_core.udl`
- `../hootkit-core/spec/spec/04-platform.md`
- `../hootkit-core/spec/spec/03-api/changes.md`
- `../hootkit-core/spec/spec/05-database.md`
- `../hootkit-core/spec/spec/06-sync.md`
- `../hootkit-core/spec/spec/07-backup.md`
- `../hootkit-core/spec/spec/11-legacy-import.md`
- `../hootkit-core/docs/spec-questions.md`、`../hootkit-core/docs/PERFORMANCE.md`
- [Android UI 层](https://developer.android.com/topic/architecture/ui-layer)
- [UniFFI 0.29 Gradle 接入](https://mozilla.github.io/uniffi-rs/0.29/kotlin/gradle.html)
- [Android 16 KB 页面支持](https://developer.android.com/guide/practices/page-sizes)
- [JNA 变更记录](https://github.com/java-native-access/jna/blob/master/CHANGES.md)


## 11. 最终实际验证与当前约束

最新默认、无筛选 forced 单测：standard493（68 fresh XML）、fdroid489（67）、公共14（3），各0 failure/error/skipped、exit0；跨 flavor996 executions /按真实 XML classname#method 去重507。8生产 variant + benchmark module forced编译、普通双debug R8及两release R8已实际通过；不是官方release签名发行或云CI/设备性能验收。

真实代表性设备矩阵覆盖27领域：36个 completed unique method各至少一次literalPASS，跨31runs共85completedexecutions=42PASS/43FAIL，另1frameworkiniterror与3startedunfinished，均保留；不是一轮同APK fresh36全PASS。最新Main业务8/8与AI修复后2/2有实际证据；记录过IME/cardlocator host辅助，不称完全自动driver。Tablet21UI证据断言/14即刻visualPNG另计，不混JUnit。API36ARM64 guest4096；16KB仅静态ELF/APK。

最终这两份普通非Acceptance APK已在自有 API36 ARM64 手机断WiFi/mobile、无clear-data/无provider请求下实际install -r，并回读guestbase.apk SHA精确匹配：冷启动Wallet100→Profile→Home正确→Settings空配置AI→force-stop重开Wallet100。两flavor共10条layoutchecks/6次Home持久化观察，089/101/111PNG实际visual；AI099/110因SECURE黑屏只用layout验证。不是10条JUnit或各flavor全部业务重新测。证据：`build/core-final-device-audit/journeys/final-ordinary-flavors.md` 与 `metadata/final-ordinary-device-verification.json`。

最终详细结果、APK路径/SHA、Core API缺口、已知问题与未测范围见 [core-migration-result.md](core-migration-result.md)。源/命令/字面输出/exit/XML/旧失败/静态检查在 `build/core-final-integration-audit`，设备所选method/raw/source/APK矩阵在 `build/core-final-device-audit/coverage-27.{json,md}`。四角色的实际执行状态以聚合verification.json为准；本段不替代hash-guarded目标前稀疏往返记录。

Core/Spec两个HEAD/pin维持第1节原值，状态只读核对clean；未在共享仓库运行cargo复现或修改源。Core已知#62/#63/#70主证与导入回执/功能APIgap详列最终结果。Core历史PERFORMANCE.md不是当前Android设备性能结论。App包装/runner/nav/AI取消调度修复有前后真实记录，不能归成Core已修。Currency.convert/Home、预算>24历史/精确周期细分、平台prefs/assets实际恢复均不属于接口缺口。

文档仅MD改动；本阶段runtime-docs-correction保全不替换whole-goal original，结果文档whole-goal原件缺失明确交聚合。自身四角色在私有fixture往返，最终全任务角色执行状态由verification管理；不通过git HEAD reset吞入既有用户/foundation变更。
