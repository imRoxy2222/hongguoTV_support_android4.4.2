# 红果 TV 原生版

Kotlin + Android 原生 View + Media3，单 APK，本分支最低 Android 4.4.2 / API 19（由主线 API 21 下调）。包名 `com.hongguotv.nativeapp`；界面功能见 [项目说明](../README.md)，合并策略与验证见 [集成记录](MAIN_INTEGRATION.md)。

## 使用

设置 → 账户 → 登录与账户管理，选择手机红果 App 扫码并在手机确认。登录态加密保存在本机，不进入片单备份；Android 5 使用 Keystore RSA 包装 AES 密钥，Android 6+ 使用 Keystore AES。退出账号清除账号数据，不删除本机收藏或历史。

收藏与观看历史有本机／账户页签。账户预约区分待上线与已上线；相关作品支持待上线预约。正式播放会同步账号进度，首集试播不写正式历史。

遥控器确定暂停／继续，左右快退快进，向下选集、向上打开操作。设置中可选择画面旋转、常驻进度、倍速、清晰度和跨季连播。自动更新来自 `N3urda/hongguoTV`，由系统确认安装。

## 构建

必须使用 JDK 17。AGP 8.11.0 + Gradle 8.14.3 的 Kotlin DSL 无法解析 `25.0.2` 这类版本串，会抛 `java.lang.IllegalArgumentException: 25.0.2`；请勿替换为更高版本的 JDK。Android SDK 36 platform 与 Build Tools 35.0.0 / 36.0.0 通过腾讯云 AndroidSDK 镜像安装，Gradle 发行包改为华为云镜像，Robolectric 依赖仓库改为阿里云镜像。配置 `ANDROID_HOME` 或本机 `local.properties` 的 `sdk.dir`。

```sh
./kotlin-tv/gradlew -p kotlin-tv :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

签名包使用 `scripts/build-kotlin.sh`。签名配置来自未跟踪的 `kotlin-tv/keystore.properties`（storeFile/storePassword/keyAlias/keyPassword），或 HONGGUOTV_KEYSTORE、HONGGUOTV_STORE_PASSWORD、HONGGUOTV_KEY_ALIAS、HONGGUOTV_KEY_PASSWORD 环境变量。不要提交签名材料。正式包必须沿用原证书；debug 包不能覆盖原签名包。

本分支以 API 19 为兼容目标：`minSdk` 由 21 下调为 19 并启用 MultiDex（`androidx.multidex:multidex:2.0.1`，Application 改为自定义 `HongguoTvApplication`），Media3 由 1.8.0 降为 1.4.1（最后一个声明 minSdk 19 的版本），OkHttp / MockWebServer 由 4.12.0 降为 3.12.13，并通过 `OkHttpCompat` / `HttpCompat` 兼容层保持 44 个扩展调用点与约 500 处 getter 迁移不改业务逻辑。`decoder-ffmpeg` 的 native FFmpeg JNI 构建已禁用（NDK r27 不支持 API 19），`FfmpegLibrary.isAvailable()` 返回 false，Android 4.4.2 回退平台解码器；`FeedPlayback` 取消 Media3 1.5+ 的预加载／预缓冲排序以降低 KitKat 电视内存压力。这两处是明确的功能降级。compileSdk / targetSdk 仍为 36，versionCode 13 / versionName 0.11.1 不变。详见 [兼容性说明](ANDROID_COMPATIBILITY.md) 与 [decoder-ffmpeg 说明](decoder-ffmpeg/README.md)。

真实 Android 4.4.2 电视尚未做任何实机验证：TLS 1.2 握手与老系统证书链、平台音频解码器能力、遥控器／焦点、安装流程、MultiDex 冷启动与内存占用均需实机验收。`:core:test` 曾在本分支构建成功；app 模块编译、`:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug` 仍由主代理验证中，结果未定。源码／构建兼容不等于真机验收，不能把 JVM 测试或 lint 等同于硬件验证。

## 发布

[发布流程](AUTO_UPDATES.md)沿用主线单仓库 Release。源代码推送或 PR 本身不会发布 APK；正式标签才触发发布。可先在本地完成构建、签名与发布材料校验，用户合并后再发布。

[第三方声明](NOTICE.md) · [GPL-3.0](LICENSE) · [原功能分支验证](UPSTREAM_SYNC.md)
