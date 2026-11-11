# Android 5 AAC compatibility decoder

> **本分支改造说明（`feat/android-4.4.2-api19`，API 19）**：本分支已禁用 `decoder-ffmpeg` 的 native FFmpeg JNI 构建。`build.gradle.kts` 移除了 `ndkVersion 27.1.12297006`、`jniLibs.srcDir("build/native-libs")`、`buildNativeAudio` Exec task 及其 `preBuild` 依赖，minSdk 由 21 改为 19，Media3 依赖改为 1.4.1；原因是 NDK r27 不支持 API 19。Java 扩展类仍编译，但 `FfmpegLibrary.isAvailable()` 返回 `false`，Android 4.4.2 回退平台解码器——这是明确的功能降级。本分支不再执行 `tools/build_native.py` 的编译流程，也不再产出 armeabi-v7a / arm64-v8a / x86 / x86_64 四个 ABI 的 FFmpeg 库。以下英文正文（含官方模拟器 AAC 解码器问题、来源许可与构建步骤）为**主线 API 21 基线的历史背景**，保留供溯源与第三方许可声明，不表示本分支仍构建或分发 native 解码器。

## Historical background (API 21 baseline)

The official Android 5.0/5.1 ARM64 emulator's `OMX.google.aac.decoder` can spin in
`libstagefright_soft_aacdec.so/dit_fft` with zero output. The app uses the Media3
FFmpeg audio renderer **only on API 21–22**, for both feed preview and episode
playback. Newer Android versions retain the platform renderer. Only FFmpeg's AAC
decoder and PCM resampling are enabled; no network, demuxer, video decoder, or
external codec library is compiled.

## Sources and licenses

- Java classes, JNI bridge and JNI keep rules: AndroidX Media **1.8.0**, from
  https://github.com/androidx/media/tree/1.8.0/libraries/decoder_ffmpeg
  (Apache 2.0; `LICENSE-AndroidX`). The experimental video renderer is omitted.
- FFmpeg **6.1.6**: https://ffmpeg.org/releases/ffmpeg-6.1.6.tar.xz
  (LGPL 2.1-or-later; `LICENSE-FFmpeg`). No GPL-only or nonfree FFmpeg component
  is enabled. Source SHA-256:
  `d4fcb164028dd3beee5d92c0ac72e46aac6973c75ea12dc14de07bf8f407370a`.
- This application's code remains GPL-3.0. Release source ZIPs include the exact
  FFmpeg source archive as well as all wrapper sources and build scripts.

## Build / replace the decoder

Use Python 3.12+, curl, make, JDK 17 and Android NDK **27.1.12297006** on Linux or
macOS (Windows: WSL). Install the NDK with `sdkmanager 'ndk;27.1.12297006'`.
Gradle's `preBuild` invokes `tools/build_native.py`, verifies the source checksum,
and builds API 21 libraries for armeabi-v7a, arm64-v8a, x86 and x86_64. Intermediate
sources and native binaries stay in ignored `build/`. ELF libraries use 16 KB
alignment and have no dependency on a separately installed C++ runtime.

For an offline rebuild from a release source ZIP, first copy
`third-party/ffmpeg-6.1.6.tar.xz` into `kotlin-tv/decoder-ffmpeg/build/`.

To rebuild a modified FFmpeg version, update VERSION/SHA256 in the script and
remove this module's `build/` directory. Rebuild the complete app with the documented
Gradle command; all inputs required to replace/relink the decoder are provided.
Do not silently package an APK without the four native libraries.

`consumer-rules.pro` preserves the renderer constructor, JNI names and the Java
buffer-growth callback called by JNI. Verify the **minified release** APK, since
an unminified debug run would not catch a missing JNI keep rule.
