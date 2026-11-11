# 登录、预约功能合入 main（2026-11-11）

## 本分支后续改造（feat/android-4.4.2-api19，API 19）

> 本文是 2026-11-11 主线功能合入的历史记录。下文所有测试数字、版本与结论均为当时主线的真实结果，**不适用于当前分支**，请勿把它们当作本分支的验证结论或当前配置。

本分支在主线基础上把最低兼容目标由 Android 5.0 / API 21 下调为 Android 4.4.2 / API 19，与上述历史记录的主要差异如下：

- 最低 SDK：`minSdk` 21 → 19；启用 MultiDex（`androidx.multidex:multidex:2.0.1`），`AndroidManifest.xml` 的 `application android:name` 改为自定义 `.HongguoTvApplication`（继承 `MultiDexApplication`）。`compileSdk` / `targetSdk` 仍为 36。
- 依赖版本：Media3 1.8.0 → 1.4.1（最后一个声明 minSdk 19 的版本），OkHttp / MockWebServer 4.12.0 → 3.12.13，`core` 同步降级；新增 `OkHttpCompat.kt`（重实现 OkHttp 4.x Kotlin 扩展，覆盖 44 个调用点）、约 500 处 OkHttp 3.12 Java getter 迁移、`HttpCompat.kt`（集中式 OkHttpClient 工厂，9 处生产构造点改走它）；Robolectric 依赖仓库改为阿里云，Gradle 发行包改为华为云镜像。
- 运行时兼容：`HongguoTvApplication` 在 SDK < 21 启用 `Tls12SocketFactory`（按 TLSv1.2 / TLSv1.1 / TLSv1 顺序启用协议，不关闭证书校验）；`NetworkCompat.register()` 按 SDK 分支（≥ 24 默认网络回调、21–23 网络请求回调、< 21 no-op），`MainActivity` 的 `networkCallback` 改为 nullable；`NetworkCompat.lanAddress()` 与 `LanInputAddresses.kt` 在 < 21 走 `NetworkInterface.getNetworkInterfaces()` 旧路径。
- 明确的功能降级：`decoder-ffmpeg` 移除 `ndkVersion 27.1.12297006`、`jniLibs.srcDir("build/native-libs")`、`buildNativeAudio` Exec task 与 `preBuild` 依赖，minSdk 21 → 19、Media3 → 1.4.1；native FFmpeg audio JNI 构建被禁用，Java 扩展类仍编译但 `FfmpegLibrary.isAvailable()` 为 false，Android 4.4.2 回退平台解码器。`FeedPlayback.kt` 删除 Media3 1.5+ 的 `DefaultPreloadManager` / `TargetPreloadStatusControl`，改为 `ExoPlayer.Builder` + `linkedMapOf<String, MediaSource>()` 复用已构建 MediaSource，取消预缓冲／预加载排序以降低 KitKat 电视内存压力。
- 更新与发布：元数据 minSdk 改为 19，APK 文件名 `hongguotv-kotlin-<version>-android4.apk`（客户端解析仍兼容历史 `android5.apk` / `android8.apk` 文件名），release notes 说明首次需手动同签名覆盖安装。
- 版本：历史记录中的「0.10.1 / 11、minSdk 21」为当时值；本分支 versionCode 13 / versionName 0.11.1 不变。
- 验证状态：下文「333 项通过」「207 项通过」「18 项通过」「lint 0 错误」「Release R8 构建成功」「API 36 TV 模拟器实测」等均为主线当时的结果，不构成本分支结论。本分支仅 `:core:test` 曾构建成功；app 模块编译、`:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug` 仍由主代理验证中，结果未定；真实 Android 4.4.2 电视未做任何实机验证（TLS 1.2 握手与证书链、平台音频解码器、遥控器／焦点、安装流程、MultiDex 冷启动、内存占用均需实机验收）。详见 [Android 兼容性说明](ANDROID_COMPATIBILITY.md)。
- 未变项：包名 `com.hongguotv.nativeapp`、原发布签名配置（证书 SHA-256 `3579f7a8a91bbbc44688db60fb16a5561921784af23fa92f39b849086d76ca98`）保留。

## 路径选择

在已通过账号实测的 `codex/login-drama-reservations`（`a0d871a`）上创建 `codex/login-reservations-main`，合入 main `d7cb34f`。功能分支相对 main 涉及约 200 个文件、3.5 万新增行；合并时有 16 个冲突文件。以功能分支为基础处理冲突，比从 main 逐项重新移植整套账户、推荐、播放与测试代码工作量小。最终 PR 目标仍为 main。

## 集成结果

- 保留扫码登录、账户收藏、历史续播、已上线／待上线预约、推荐试播、搜索联想、排行榜、相关季、评论与弹幕。
- 保留登录后设置状态原位刷新，以及收藏／历史／预约预览与卡片统一集数的修复。
- 保留 main 的 Android 5.0/API 21 最低要求、原包名及发布签名、单仓库更新和发布流水线。媒体库采用兼容 API 21 的 Media3 1.8.0，适配新功能调用。
- 为 Android 5 账号存储增加 Keystore RSA 包装 AES 密钥；新系统保留原 AES Keystore 路径。补齐网络、系统服务、开关配色、滚动监听的版本兼容。
- 将 main 的画面旋转、常驻播放进度、完整标题入口与显示偏好持久化接入新界面。默认旋转仍为 0°，常驻进度默认关闭。
- 保留 App 搜索游标与类型隔离；短剧官网回退支持已有类型字段筛选、旧响应兼容、精确标题优先。主线旧播放候选解析与设备注册辅助路径及其测试仍保留，正常播放使用新分支共享设备会话和有界恢复流程。
- CI 和本地构建增加 Android 单元测试。公开仓库的构建保持开启；没有修改其他私有项目。

## 验证

- 核心测试：333 项通过。
- Android Robolectric 测试：207 项通过，无跳过；兼容性测试覆盖 API 21、23、26，账户列表与状态回归覆盖 API 21、26。
- 发布协议 Python 测试：18 项通过（发布操作为 mock）。
- Debug / Release lint 均 0 错误；现有警告见构建报告。
- Release R8 构建成功；包名 `com.hongguotv.nativeapp`，版本暂沿用 main 的 0.10.1 / 11，minSdk 21；签名 SHA256 为 `3579f7a8a91bbbc44688db60fb16a5561921784af23fa92f39b849086d76ca98`。
- API 36 TV 模拟器同签名覆盖安装后登录态保留，设置显示已登录；账户收藏加载 40 部，预览与卡片集数一致；预约加载已上线 18 部、待上线 5 部；设置显示旋转和常驻进度入口。未操作账号收藏或预约增删。

## 边界与发布

Android 5 真机的 Keystore 加解密、硬件解码、旋转渲染及安装尚未实测；Robolectric 与 lint 不能代替这些验收。本轮不重新扫码、不退出账号，也未覆盖预约新增／取消的真实写操作。上游不再展示旧“稍后看”和片单备份入口，底层数据格式保留；伴机服务需单独部署，未验收。

本次仅提交 main 的合并 PR，由仓库所有者合并。之后再确定下一正式版本、构建发布并验证 Release；本测试包不作为正式 Release。发布前应检查现有 preview 标签被标为 latest 的情况与发布脚本的稳定版本比较约束。
