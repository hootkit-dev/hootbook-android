# CI 凭证配置

本次只接通 CI 的凭证引用，未生成 PAT、未保存真实 Secret，也未执行发布或云设备任务。App 仓库公开，`hootkit-dev/hootkit-core` 私有；CI 按 `core-bindings/core.lock.json` 的固定 commit 拉取 Core，不改 Core 仓库、CoreSpec 或 pin。

## 1. 必需：`CORE_REPO_READ_TOKEN`

App 仓库的自动 `GITHUB_TOKEN` 仅用于本仓库，不能代替跨私有仓库的 Core 读取凭证。请由拥有 Core 读取权限的账号创建一个专用 **fine-grained PAT**：

1. 打开 [GitHub fine-grained PAT 页面](https://github.com/settings/personal-access-tokens/new)（头像 → Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token）。
2. Token name 可填 `hootbook-ci-core-read`；设置有效期，例如 **30 天或 90 天**，服从组织的更短上限，到期前轮换。
3. **Resource owner：`hootkit-dev`**。若列表没有该组织，检查账号组织成员身份及组织的 PAT 策略。
4. **Repository access：Only select repositories → 只选择 `hootkit-core`**。不选择 App/CoreSpec，不选 All repositories。
5. **Repository permissions → Contents：Read-only**。Metadata 的读取权限由 GitHub 自动包含；无需 Contents write、Actions write 或其他写入权限。
6. 生成并保存 token；若组织要求审批，等待组织管理员批准后再测试。`pending` 状态的 token 尚无私库读取权限；已有账号权限仍是上限。
7. 打开 [App 仓库 Actions Secrets](https://github.com/hootkit-dev/hootbook-android/settings/secrets/actions) → Settings → Secrets and variables → Actions → **New repository secret**。
8. Name 必须完整填写 **`CORE_REPO_READ_TOKEN`**，Secret 填上刚生成的 token，点击 Add secret。

这里应使用 **Repository secret**，不是 Variable，也不放进 `KEYSTORE` Environment：Tests、性能构建等 job 没有绑定该环境。若使用组织 Secret，应显式允许 App 仓库访问并检查组织方案支持；最直接的配置是上述 Repository secret。

CI 的 7 个 `setup-core` 调用均显式传入 `core-token`；Core 平台测试的直接 checkout 也引用同一个 Secret。两个 Core checkout 均设置 `persist-credentials: false`，凭证不保留到后续 Git 操作；不改变 App 自身 checkout 的自动 `GITHUB_TOKEN` 或原工作流权限。缺失时先报 `Missing CORE_REPO_READ_TOKEN repository secret`，不输出值；空值检测通过仅表示已配置，不保证有效期、组织审批或 Contents 权限正确。

### 可选：用 GitHub CLI 配置（由你本地执行）

浏览器添加 Secret 已足够。CLI 可用独立管理会话 `gh auth login`，不把该管理会话的 token 复制为 Core 的读取 PAT。以下在受信的本地终端运行，仅设置 Core Repository secret；Python 隐藏输入，临时文件在仓库外，Secret 值通过 stdin 传给 `gh`，不写入命令行参数、聊天或日志：

```bash
set +x
umask 077
SECRET_DIR=$(mktemp -d "${TMPDIR:-/tmp}/hootbook-ci-secrets.XXXXXX")
trap 'rm -rf "$SECRET_DIR"' EXIT
python3 - "$SECRET_DIR/core-token" <<'PY'
import getpass
import pathlib
import sys
value = getpass.getpass("Paste the dedicated Core read-only PAT (hidden): ")
if not value:
    raise SystemExit("Empty PAT")
pathlib.Path(sys.argv[1]).write_text(value)
PY
gh secret set CORE_REPO_READ_TOKEN \
  --repo hootkit-dev/hootbook-android < "$SECRET_DIR/core-token"
rm -f "$SECRET_DIR/core-token"
```

轮换：生成相同最小权限的新 PAT → 更新同名 Secret → 验证一次受信 CI checkout → 撤销旧 PAT。若泄露，立即撤销并更新，勿等待轮换日期。

## 2. 发布签名：已有 `KEYSTORE` Environment 的四个 Secret

这四项是现有签名流程需要的配置，不是 Core token 接线的一部分。打开 [App Environments](https://github.com/hootkit-dev/hootbook-android/settings/environments) → **KEYSTORE** → Environment secrets：

| Name | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | **已确认的 Hootbook keystore 文件**的完整、单行 Base64 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 已确认的 Hootbook key alias |
| `KEY_PASSWORD` | 已确认的 Hootbook key 的密码 |

仓库所有者已明确确认以当前 keystore 作为 Hootbook 的签名身份，替换从 Cashiro 继承的旧证书指纹。确认依据为[公开证书诊断](https://github.com/hootkit-dev/hootbook-android/actions/runs/38130958758)，诊断仅读取公开证书，未提取私钥。签名预检及 Debug/main、Testing、Release 的 APK 验证统一固定以下公开证书 SHA-256：

```text
f3bd2660565ef8dc23e61ef5c7b48b34e1cb21244b97847d5da008ae8a9ea3a5
```

同包名、不同签名的旧 APK 通常不能直接覆盖升级；更新预期指纹不会提供签名兼容性，也不会改变已安装 APK 的签名。后续应保留已确认的 Hootbook 私钥，勿为通过 CI 新建替代 key 或擅自改指纹。

在已安装 JDK 的本地终端检查已确认的 Hootbook 证书（替换下面的私有路径和 alias，不把密码写入参数；`keytool` 会交互提示输入 keystore 密码）：

```bash
keytool -list -v -keystore /PRIVATE/PATH/release.keystore -alias RELEASE_ALIAS
```

将输出证书的 SHA256 去掉冒号并按大小写不敏感方式与上述值比较；只记录公开的证书指纹，不记录密码或私钥。

若用 CLI 上传，先在私有临时目录编码，macOS/Linux 二选一执行，不把 keystore 或编码结果提交到 Git：

```bash
set +x
umask 077
SIGNING_DIR=$(mktemp -d "${TMPDIR:-/tmp}/hootbook-signing.XXXXXX")
trap 'rm -rf "$SIGNING_DIR"' EXIT
# macOS：
base64 -i /PRIVATE/PATH/release.keystore | tr -d '\r\n' > "$SIGNING_DIR/keystore.base64"
# Linux（替代上一行）：
# base64 -w 0 /PRIVATE/PATH/release.keystore > "$SIGNING_DIR/keystore.base64"
gh secret set KEYSTORE_BASE64 --repo hootkit-dev/hootbook-android \
  --env KEYSTORE < "$SIGNING_DIR/keystore.base64"
rm -f "$SIGNING_DIR/keystore.base64"
# 以下命令分别通过 gh 的交互提示输入值，不用 --body 携带真实密码：
gh secret set KEYSTORE_PASSWORD --repo hootkit-dev/hootbook-android --env KEYSTORE
gh secret set KEY_ALIAS --repo hootkit-dev/hootbook-android --env KEYSTORE
gh secret set KEY_PASSWORD --repo hootkit-dev/hootbook-android --env KEYSTORE
```

建议在 KEYSTORE 的 Deployment branches/tags 中只允许受信分支和发布 tag，并按团队流程设置审核。此文档不修改环境保护策略或现有签名工作流。

## 3. 仅对应功能需要的可选 Secret

- **`PLAY_STORE_SERVICE_ACCOUNT_JSON`**：仅 Release 勾选 `upload_to_play_store` 时使用；填完整服务账号 JSON。Release 绑定 KEYSTORE，可以存为 **KEYSTORE Environment secret**；账号须按 Play Console 配置必要的发布权限。不开 Play 上传时不需要这项。
- **`DEVICE_RUN_SERVICE_ACCOUNT`**：仅手动 Performance 的 `benchmark`/`profile` 设备任务使用；填完整服务账号 JSON，存为 **Repository secret**（或允许 App 的组织 Secret），因为性能 job 未绑定 Environment。相关云项目 API、IAM 和计费按 `.github/workflows/perf-device.yml` 说明配置。`apks` 模式仅构建 APK，不执行云设备测试；运行设备任务会产生计费，需另行确认。
- **`GITHUB_TOKEN`**：GitHub Actions 自动提供，不手工新建同名 Secret。
- Java **21**、Rust **1.97.0**、NDK **28.2.13676358** 由现有 CI 配置/锁文件安装，不是 Secret。

JSON 文件也可以按需通过 stdin 上传，无需把内容放在命令参数中：

```bash
# 可选功能实际需要时才执行，文件保存在仓库外的私有目录。
gh secret set PLAY_STORE_SERVICE_ACCOUNT_JSON --repo hootkit-dev/hootbook-android \
  --env KEYSTORE < /PRIVATE/PATH/play-service-account.json
gh secret set DEVICE_RUN_SERVICE_ACCOUNT --repo hootkit-dev/hootbook-android \
  < /PRIVATE/PATH/device-run-service-account.json
```

## 4. 配置后的验证与触发范围

只查看名称，不读取或打印 Secret 值：

```bash
gh secret list --repo hootkit-dev/hootbook-android
gh secret list --repo hootkit-dev/hootbook-android --env KEYSTORE
```

Secret 更新本身不会自动重跑过去失败的 workflow。当前触发器保持原样：

| Workflow | 当前触发方式与注意事项 |
| --- | --- |
| Tests | `main` push，或目标为 `main` 的 PR；没有手动 dispatch。当前 `codex/hootbook-v1` push 不触发 Tests。 |
| Debug APK | `main` push、PR、手动 dispatch；只有 `main` ref 使用发布签名并发布 rolling debug release。 |
| Testing APK | 仅手动；绑定 KEYSTORE，会构建和发布 testing APK。 |
| Release | `v*` tag push，或手动；job 仅在 `main` 或 `v*` tag ref 执行。不会因普通 `main` push 自动运行；在 `codex/hootbook-v1` 手动运行 job 会跳过。发布流程可能创建 tag/release 或上传 Play。 |
| Performance (device) | 仅手动；`benchmark`/`profile` 可能启动计费设备任务，`apks` 仅构建。 |

推荐你配置 Core Secret 后自行创建一个**同仓库、目标 `main` 的 PR**来运行 Tests，检查 Core checkout 与后续任务。本文不自动创建 PR、发布 release 或启动云设备。若重跑旧失败 run，该 run 仍使用旧提交的 workflow；Secret 接线验证需要运行包含本次改动的提交。

Fork PR 不获得 Secrets：Tests 两个 job 与 Debug build job 显式跳过 fork PR。同仓库 PR 仍沿用现有触发方式，应只允许受信协作者提交；不要通过 `pull_request_target` 或把 PAT 注入 fork 代码来绕过隔离。

常见定位：

- `Missing CORE_REPO_READ_TOKEN`：检查是否在 App 的 Repository secrets 中、名称完全一致、不是 Variable/KEYSTORE-only Secret。
- Core checkout 401/403/404：检查 token 过期/撤销、owner、所选 Core 仓库、Contents Read-only、账号权限、组织审批和组织 PAT 策略；私仓无权限时也可能表现为 404。
- Secret 名称存在但 Tests 未启动：检查事件是否符合上表；Secret 配置和分支 push 不改变触发器。
- 签名指纹不一致：确认上传的是所有者已确认的 Hootbook keystore 和 alias，不修改工作流中的预期指纹来掩盖意外换 key。

参考：[GitHub 创建 PAT](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)、[Actions Secrets](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions)、[gh secret set](https://cli.github.com/manual/gh_secret_set)、[actions/checkout 跨私库示例](https://github.com/actions/checkout#checkout-multiple-repos-private)。

## Early signing verification

Testing and release workflows run `.github/actions/signing-preflight` immediately after Java setup,
before the private Core checkout, Rust/NDK work, or Gradle build. The validator checks Base64,
keystore loading, password/private-key alias, signing and verification, and the owner-confirmed Hootbook
certificate fingerprint. A successful decode is not sufficient proof that the keystore password is
correct. Credentials remain in environment variables; they are not put into Gradle command-line
arguments. Final APKs are independently checked with apksigner and aapt2.

If preflight rejects a secret, an authorized administrator must correct it. Do not repeatedly run
full builds to diagnose a known signing failure. This change does not inspect or modify repository
secrets. No Core or Spec source/version change is required. The source pin remains in
`core-bindings/core.lock.json`; the versioned SDK consumer is deferred until an authorized SDK exists.

Public workflow caches are limited to downloaded Gradle dependencies and the wrapper. Private Core
Rust intermediates and Gradle build output caches are not published to Actions cache.
