# Fork 的版本号、标签与自动发布

本文只描述**本 fork（imRoxy2222/hongguoTV_support_android4.4.2）**的发布方式。上游 N3urda/hongguoTV 的
发布流程见 `kotlin-tv/AUTO_UPDATES.md`，两套流水线互不影响。

## 1. 分支模型

| 分支 | 用途 | 跟踪 |
| --- | --- | --- |
| `main` | 只做 `upstream/main` 的镜像，**不写自己的代码** | `upstream/main` |
| `feat/android-4.4.2-api19` | 自己的开发分支，所有改动都在这里 | `origin/feat/android-4.4.2-api19` |

origin 上只保留这两个分支，其余历史分支已删除；它们在上游 `upstream` 仍有同名同 SHA 副本，
需要时可 `git fetch upstream <分支名>` 取回。

## 2. 上游 main 更新后的同步流程

约定：`main` 始终保持与上游一致；依赖版本差异大，所以**先在 `feat` 上改依赖再合并**。

```sh
cd /home/frk2222/code/hongguoTV_support_android4.4.2

git checkout main
git fetch upstream --prune --tags
git merge --ff-only upstream/main
git push origin main                          # 可选：让 GitHub 上的 main 与上游同步

git checkout feat/android-4.4.2-api19
git merge main                                # 出现冲突时先按需改依赖版本再继续
git push origin feat/android-4.4.2-api19
```

判定是否需改依赖：`git log --oneline feat/android-4.4.2-api19..main -- kotlin-tv/*/build.gradle.kts kotlin-tv/gradle/libs.versions.toml`。

## 3. 发布一个版本：打标签就够

### 3.1 版本号怎么来

`versionName` / `versionCode` 的唯一来源是 `kotlin-tv/app/build.gradle.kts`，合并上游后**天然与上游版本一致**，
不要手改。上游 tag 是 `kotlin-v0.13.2`，本 fork 打成 `a44-v0.13.2`（前缀 `a44` = Android 4.4.2）。

### 3.2 操作步骤

```sh
cd /home/frk2222/code/hongguoTV_support_android4.4.2

# 1) 确认工作区干净、已合并最新上游
git checkout feat/android-4.4.2-api19
git status
grep -n "versionCode\|versionName" kotlin-tv/app/build.gradle.kts
# 输出应形如 versionCode = ... ?: 17 / versionName = ... ?: "0.13.2"

# 2) 打标签（标签版本号 = 上面看到的 versionName）
git tag a44-v0.13.2

# 3) 只推这一个标签（切勿 git push --tags，会把上游 kotlin-v* 标签一起推上去）
git push origin a44-v0.13.2
```

推送标签后 `.github/workflows/fork-release.yml` 自动执行：

1. 校验标签格式 `a44-vX.Y.Z`，且与源码 `versionName`、`minSdk 19` 一致，不一致直接失败；
2. 从 Secrets 还原签名密钥到 runner 临时目录（结束即删）；
3. 跑 `:core:test`、`:app:testDebugUnitTest`、`:app:lintRelease`、`:app:assembleRelease -PreleaseAbis=true`；
4. 逐个 APK 校验包名、版本、`minSdk 19`、**v1(JAR) 签名**（KitKat 必需）；
5. 创建 Release 并上传：

| 附件 | 说明 |
| --- | --- |
| `hongguotv-kotlin-<ver>-android4.apk` | 全 ABI，电视首选 |
| `hongguotv-kotlin-<ver>-arm32-v7a.apk` | 仅 ARM32 |
| `hongguotv-kotlin-<ver>-arm64-v8a.apk` | 仅 ARM64 |
| `hongguotv-<tag>-source.zip` | 对应提交完整源码（GPL-3.0 要求） |
| `SHA256SUMS` | 全部附件的 SHA-256 |

整个过程约 10–20 分钟，Actions 页面可看实时日志。

## 4. 一次性配置：4 个仓库 Secret

签名密钥已生成，位于 WSL 内 `~/hongguotv-fork-signing/`：

- `fork-release.jks`：密钥本体（**不要提交进仓库**，`.gitignore` 已忽略 `*.jks`）
- `SECRETS.txt`：列好 4 个 Secret 的完整值，直接用编辑器打开复制

Windows 路径：`\\wsl$\Debian\home\frk2222\hongguotv-fork-signing\SECRETS.txt`

到 GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret，逐个添加：

| Secret 名 | 内容 |
| --- | --- |
| `HONGGUOTV_KEYSTORE_B64` | `fork-release.jks` 的 Base64（`SECRETS.txt` 里整行很长的值） |
| `HONGGUOTV_STORE_PASSWORD` | keystore 口令 |
| `HONGGUOTV_KEY_ALIAS` | 固定为 `hongguotv-a44` |
| `HONGGUOTV_KEY_PASSWORD` | 与 keystore 口令相同 |

配置完成后密钥只存在于 GitHub Secrets，日常无需再碰本地文件；建议本地保留 `fork-release.jks` 作离线备份
（丢了就无法再发布同签名的更新包，只能换签名并让用户卸载重装）。

缺任一 Secret 时 workflow 会在第一步明确报错并停止，不会产出未签名包。

## 5. 打错了怎么办

```sh
# 标签打错（尚未推送）：本地删除重打
git tag -d a44-v0.13.2

# 已推送且 Release 已生成：先删 Release，再删远端标签
git push origin :refs/tags/a44-v0.13.2
```

注意：workflow 检测到同名 Release 已存在时会**拒绝覆盖**并失败，这是刻意设计，避免已发布产物被替换。

## 6. 已知限制

1. **签名与上游不同。** 本 fork 用自己的证书，从上游 APK 切过来必须**先卸载**上游版本（会清除本机记录），
   不能覆盖安装；此后本 fork 的版本之间可以正常覆盖升级。
2. **应用内自动更新仍指向上游** `N3urda/hongguoTV`（`kotlin-tv/core/.../AppUpdates.kt` 的 `REPOSITORY`）。
   客户端会校验已安装应用签名，因此不会误装上游 APK，只是本 fork 无法自动更新自己。
   若以后要走自更新，需改该常量指向本仓库、并在 Release 里补 `update.json`（schema 与上游一致）。
3. **versionCode 跟随上游**（0.13.2 对应 17）。若同一上游版本要重发一次，需先手动递增
   `kotlin-tv/app/build.gradle.kts` 的默认 `versionCode`，否则新包无法覆盖旧包。

## 7. 相关文件

- `.github/workflows/fork-release.yml`：本 fork 的发布流水线（标签触发）
- `.github/workflows/kotlin-release.yml`：上游流水线，`github.repository == 'N3urda/hongguoTV'` 保护，
  fork 上永不运行，保持与上游一致以便合并
- `.github/workflows/kotlin-native.yml`：分支/PR 上的构建验证（不发布）
