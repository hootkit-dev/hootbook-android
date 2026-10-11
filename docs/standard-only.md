# Standard-only Android build

## 本次增量

- 移除 F-Droid product flavor、专用 capability module 与已无用途的 variant 状态；保留 standard 的 Firebase v2/Auth 能力与正常离线行为。
- CI 测试、release workflow、本地 release 脚本和性能缓存源码范围只引用 standard；不再构建、重命名、校验或上传 F-Droid APK。standard APK 命名、校验和及发布流程保持。
- release / preview / benchmark / profiling build types、standard Gradle task 名称、Core benchmark 初始化与完整兼容 Core baseline 规则保持。
- 不修改 Core / Spec / pin，不增加版本号。本次未发布、上传或触发远程 workflow。

## 本次验证与边界

本次本地 task model 实测 F-Droid tasks **528 → 0**、configurations **200 → 0**、源码 bindings **11 → 0**；standard tasks **582 → 582**。五个 standard variants（debug / release / preview / benchmark / profiling）的 applicationId、versionName、minify 与 ABI outputs 逐项保持原值。来源：`build/core-standard-only-build-audit/model-summary.json` 与 before/after task logs（均 exit 0）。

本次 fresh 默认、无筛选单测：

| 范围 | Fresh XML | tests | failure / error / skipped | exit |
|---|---:|---:|---|---:|
| standard App | 68 | 493 | 0 / 0 / 0 | 0 |
| 公共 core-platform | 3 | 14 | 0 / 0 / 0 | 0 |

来源：`build/core-standard-only-build-audit/unit-summary.json`、`fresh-unit.{log,json}` 与本次 `test-results/` 副本。合计 **507 次执行 / 507 个唯一案例**；独立审核对本次 XML `classname#method` 去重的记录在 `build/core-standard-only-audit/unit-case-identity-summary.json`，不沿用上轮双 flavor 的 996 次执行。

本次强制执行五个 App standard build types 的 Kotlin 编译（debug / release / preview / benchmark / profiling）和 `:benchmark:compileStandardBenchmarkKotlin`，**6 个目标均实际执行、exit 0**；来源：`build/core-standard-only-build-audit/forced-five-compile.{log,json}`。另外 `:app:compileStandardDebugAndroidTestKotlin` 已强制编译 exit 0（`forced-instrumentation-compile.{log,json}`）。动态负控 `:app:assembleFdroidRelease --dry-run` 实际 exit 1，字面错误 `task 'assembleFdroidRelease' not found in project ':app'`；这是删除后的预期结果，记录在 `absent-fdroid-task.{log,json}`。

本次普通 `:app:assembleStandardDebug -PslimDebug` 的 forced Kotlin / R8 / package 实际通过 exit 0，不启用 Core acceptance。归档 APK：`build/core-standard-only-audit/artifacts/standard-ordinary-minified.apk`，**16959606 bytes**，SHA-256 `baff46329c246284d606c1c305ef8752ccd84a50b05b15ca2b7cce7d41f77eaf`。

独立 APK 静态检查 exit 0：arm64 Core loader/native pinned bytes/JNA、64-bit ELF 16 KB PT_LOAD、`zipalign -P 16`、本地 Android Debug 签名、六个 Core 系统组件与 backup/cleartext=false；旧 App engine 与已移除的 flavor 状态不在 DEX。普通 BuildConfig 为 `CORE_ACCEPTANCE=false`、`DEBUG=false`、无 `CORE_ONLY`。四 ABI AAR/native/provenance/JNA/backup 的重开校验 exit 0。来源：`build/core-standard-only-audit/runs/{ordinary-standard-r8-1,standard-apk-static-2,native-provenance-1}`；首次静态签名子进程缺 Java 21 环境的 exit 1 记录保留，纠正环境后检查通过，APK 字节未改。这里不是官方发行签名、设备 native 加载或本次 release R8 验收。

本次 delivery 本地静态校验：3 个 workflow YAML mapping/jobs/steps 解析与 **34 个 run blocks** 的 `bash -n`、release.sh 的 `bash -n` 通过。只在 private fixture 执行脚本/workflow 的 rename/checksum 片段：**5 个 standard APK** 名称与校验和保持；旧 baseline 重命名 3 个 F-Droid APK，修改后为 0。release.sh 校验后返回原目录已实际检查；没有执行 Gradle、git tag/push 或 gh release 命令。

本次没有重新安装或运行设备 APK；上轮设备结果及 APK SHA 仍保留为历史证据，不把本次新 APK 记为已完成设备验收。现有 Core API 行为没有因 variant 清理改变；缺配置/未授权的 standard 仍可离线使用。

CI/scripts 已按静态校验范围验证，不代表 GitHub Actions、官方发行签名或设备性能已运行。脚本没有执行发布命令。

## 历史迁移记录

[迁移结果](core-migration-result.md)、[接入历史](core-integration.md)和[功能矩阵](core-final-function-matrix.md)保留双 flavor 曾真实完成的单测、R8、设备与 SHA 记录；这些是历史事实，不是当前可用 variant 列表。
