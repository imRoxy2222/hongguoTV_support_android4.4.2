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

规则与自动化见 [RELEASE_POLICY.md](RELEASE_POLICY.md)：`a44-v<上游版本>-<N>`，
`versionCode = 上游 versionCode * 100 + N`。`versionName` / `versionCode` 的唯一来源是
`kotlin-tv/app/build.gradle.kts`，由 `scripts/next_version.py --write` 自动写入，**不要手改**。
前缀 `a44` = Android 4.4.2。

当前：上游 `kotlin-v0.13.2`（versionCode 17）→ 本 fork `a44-v0.13.2-1` / `0.13.2-1` / 1701。

### 3.2 操作步骤

```sh
cd /home/frk2222/code/hongguoTV_support_android4.4.2

# 1) 确认工作区干净、已合并最新上游
git checkout feat/android-4.4.2-api19
git fetch upstream --prune --tags
git status

# 2) 计算并写入版本号（不要手动改数字）
python3 scripts/next_version.py --write

# 3) 提交版本号，再按脚本输出的 fork_tag 打标签
git add kotlin-tv/app/build.gradle.kts
git commit -m "chore(release): 0.13.2-1 (versionCode 1701)"
git tag a44-v0.13.2-1

# 4) 先推分支，再单独推这一个标签（切勿 git push --tags，会带上游 kotlin-v* 标签）
git push origin feat/android-4.4.2-api19
git push origin a44-v0.13.2-1
```

推送标签后 `.github/workflows/fork-release.yml` 自动执行：

1. 校验标签格式 `a44-vX.Y.Z-N`，且与源码 `versionName`、`minSdk 19` 一致；`versionCode % 100 == N`，
   且大于上次已发布 Release 的 `versionCode`。任一不符直接失败；
2. 从 Secrets 还原签名密钥到 runner 临时目录（结束即删）；
3. 跑 `:core:test`、`:app:testDebugUnitTest`、`:app:lintRelease`、`:app:assembleRelease`
   （**不传** `-PreleaseAbis=true`：本分支 APK 内无 native 库，ABI 拆分只会复制同一份产物）；
4. 校验包名、版本、`minSdk 19`、**v1(JAR) 签名**（KitKat 必需）；
5. 创建 Release 并上传：

| 附件 | 说明 |
| --- | --- |
| `hongguotv-kotlin-<ver>-android4.apk` | 唯一安装包：通用包，含全部 ABI |
| `hongguotv-<tag>-source.zip` | 对应提交完整源码（GPL-3.0 要求） |
| `update.json` | 应用内自更新的元数据，指向上面的通用包 |
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
# 标签打错（尚未推送）：本地删除后按脚本输出重打
git tag -d a44-v0.13.2-1

# 已推送、但流水线在构建阶段失败且未产出 Release：删除远端标签后重打同一标签
git push origin :refs/tags/a44-v0.13.2-1
```

**Release 一旦创建，就不要再删标签或覆盖附件**，改用下一个修订号重发：

```sh
python3 scripts/next_version.py --force --write    # 0.13.2-1 → 0.13.2-2 / 1702
git add kotlin-tv/app/build.gradle.kts
git commit -m "chore(release): 0.13.2-2 (versionCode 1702)"
git tag a44-v0.13.2-2
git push origin feat/android-4.4.2-api19
git push origin a44-v0.13.2-2
```

注意：workflow 检测到同名 Release 已存在时会**拒绝覆盖**并失败，这是刻意设计，避免已发布产物被替换。

## 6. 已知限制

1. **签名与上游不同。** 本 fork 用自己的证书，从上游 APK 切过来必须**先卸载**上游版本（会清除本机记录），
   不能覆盖安装；此后本 fork 的版本之间可以正常覆盖升级。
2. **应用内自动更新指向本仓库**（`kotlin-tv/core/.../AppUpdates.kt` 的 `REPOSITORY` =
   `imRoxy2222/hongguoTV_support_android4.4.2`）。客户端读取 `releases/latest/download/update.json`，
   该文件由 `fork-release.yml` 每次发布时自动生成，`tag` 形如 `a44-v0.13.2-1`，`apk.name` 固定为
   `hongguotv-kotlin-<ver>-android4.apk`。客户端仍会校验 APK 包名、版本、`minSdk` 与已安装签名，
   因此只有本仓库同证书的正式包能被安装。
   注意：`AppUpdate.parse` 的 tag 正则同时接受 `kotlin-v` 与 `a44-v`；若标签前缀再次更换，需同步放宽该正则。
3. **versionCode 由规则生成**（`上游 versionCode * 100 + N`；0.13.2 的第 1 次发布为 1701），
   不再等同于上游的 17。同一上游版本重发时只增加 `N`，用
   `python3 scripts/next_version.py --force` 完成，不要手动改数字。

## 7. 相关文件

- `docs/RELEASE_POLICY.md`：版本号规则、发版判定与自动化流程（定时任务按此执行）
- `scripts/next_version.py`：计算下一个 `<上游版本>-<N>` 与 `versionCode`
- `.github/workflows/fork-release.yml`：本 fork 的发布流水线（标签触发）
- `.github/workflows/kotlin-release.yml`：上游流水线，`github.repository == 'N3urda/hongguoTV'` 保护，
  fork 上永不运行，保持与上游一致以便合并
- `.github/workflows/kotlin-native.yml`：分支/PR 上的构建验证（不发布）
