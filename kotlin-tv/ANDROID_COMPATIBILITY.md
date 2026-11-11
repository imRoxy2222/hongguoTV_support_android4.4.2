# Android 4.4 / 4.4.2 (API 19) compatibility

This branch lowers the minimum SDK of the native APK from API 21 (Android 5.0) to
API 19 (Android 4.4.2 / KitKat). `compileSdk` and `targetSdk` remain 36, and
`versionCode` 13 / `versionName` 0.11.1 are unchanged. The package name and the
original signing configuration are preserved.

This is a **source and build compatibility** target only. It is not a claim that any
physical Android 4.4.2 TV has been validated. No real-device run has been recorded
for TLS 1.2 handshakes, certificate chains on old vendor firmware, platform audio
decoders, remote/focus behaviour, installation, MultiDex cold start or memory use.
Source-level compatibility is not runtime verification.

## Scope and boundaries

- Supported floor: API 19. Devices below API 19 are not a target of this branch.
- The manifest now uses a custom `.HongguoTvApplication`, a subclass of
  `androidx.multidex.MultiDexApplication`, instead of the library class directly.
- `compileSdk`/`targetSdk` 36 are kept so modern build tooling and lint still apply;
  API 19 behaviour is handled at runtime by SDK branches, not by lowering the
  compile SDK.
- API 21+ APIs (`NetworkCallback`, `NetworkRequest`, `NetworkCapabilities`, the
  default-network callback, `connectivity.allNetworks`, `activeNetwork`,
  `ApplicationInfo.minSdkVersion`) must never be instantiated or referenced on
  API 19.

## Dependency downgrade decisions

| Concern | Before (API 21 baseline) | This branch (API 19) | Reason |
| --- | --- | --- | --- |
| Media3 | 1.8.0 | 1.4.1 | 1.4.1 is the last Media3 release declaring minSdk 19; from 1.5.0 the upstream minSdk is 21. The 1.8.0 `constants.gradle` (https://github.com/androidx/media/blob/1.8.0/constants.gradle) records the minSdk 21 baseline that had to be left. |
| OkHttp / MockWebServer | 4.12.0 | 3.12.13 | OkHttp 4.x is Kotlin-based and targets newer baselines; 3.12.13 is the Java-based 3.12 line usable on API 19. |
| Okio | `Source.buffer()` extension | Okio 1.x via `okio.Okio.buffer(source)` | The Kotlin `Source.buffer()` extension is not available; the Java static helper is used. |
| MultiDex | not enabled | `multiDexEnabled = true`, `androidx.multidex:multidex:2.0.1` | Method-reference limit on the older runtime; the custom Application installs MultiDex. |
| FFmpeg decoder JNI | built with NDK 27.1.12297006 | native build disabled | NDK r27 does not support API 19; see "Known functional downgrades". |
| Robolectric repository | upstream | Alibaba Cloud mirror | Builds use domestic mirrors; see "Build and verification". |

Compatibility shims were added so business logic did not need to change:

- `core/.../compat/OkHttpCompat.kt` re-implements the OkHttp 4.x Kotlin extensions
  (`toMediaType`, `toRequestBody`, `toResponseBody`, `toHttpUrl`, `toHttpUrlOrNull`,
  ...) on top of OkHttp 3.12 Java static APIs, covering 44 call sites.
- About 500 OkHttp 3.12 Java getter migrations were applied
  (`response.code()`/`body()`, `request.url()`, `cookie.name()`,
  `dispatcher()`/`connectionPool()`, `executorService()?.shutdown()`, ...).
- `core/.../HttpCompat.kt` is a central `OkHttpClient` factory (`newBuilder()` /
  `newClient()`) that injects the API 19 `SSLSocketFactory`; all 9 production client
  construction sites go through it.

## Runtime compatibility layer

- **TLS 1.2 on API 19.** API 19 supports TLS 1.2, but newly created sockets enable
  only TLS 1.0 by default, and modern APIs/CDNs may refuse it. For `SDK_INT < 21`,
  `HongguoTvApplication` installs a custom `Tls12SocketFactory` that enables
  `TLSv1.2`, `TLSv1.1`, `TLSv1` in order on each `SSLSocket`. Certificate
  verification and the trust chain are **not** disabled or weakened.
- **Network callbacks.** `NetworkCompat.register()` branches by SDK: `>= 24` uses
  `registerDefaultNetworkCallback`, `21..23` uses
  `registerNetworkCallback(NetworkRequest...)`, and `< 21` is a direct no-op.
  `MainActivity` keeps the `networkCallback` nullable (null on API 19) and only
  registers/unregisters it when non-null.
- **LAN address lookup.** `NetworkCompat.lanAddress()` and `LanInputAddresses.kt`
  use `NetworkInterface.getNetworkInterfaces()` on API 19, skipping
  `connectivity.allNetworks` (API 21+). Phone input and backup still require a
  private IPv4 Wi-Fi/Ethernet address.
- **Retained from the API 21 baseline.** Named system services on API 21/22
  (including the artwork memory budget); resolving the connected network on API
  21/22 without `activeNetwork` (API 23); on API 21–25, launching the system
  package installer with a scoped FileProvider URI while API 26+ keeps per-app
  permission; APK checksum, package, version and signing-certificate checks on all
  versions; and Java core-library desugaring for modern Java APIs in
  core/dependencies. `ApplicationInfo.minSdkVersion` remains available only on
  API 24+, where it must match update metadata; earlier versions rely on the OS
  installer and on metadata checked against the running SDK before download.

## Known functional downgrades

These are deliberate and must be stated plainly; they are not regressions to be
silently fixed by loosening checks.

- **FFmpeg software audio decoding is disabled.** In `decoder-ffmpeg`, the
  `ndkVersion 27.1.12297006`, `jniLibs.srcDir("build/native-libs")`, the
  `buildNativeAudio` Exec task and its `preBuild` dependency were removed; the
  module minSdk is now 19 and its Media3 dependency is 1.4.1. Java extension
  classes still compile, but `FfmpegLibrary.isAvailable()` returns `false`, so
  Android 4.4.2 falls back to the platform decoder. See
  [decoder-ffmpeg/README.md](decoder-ffmpeg/README.md).
- **Preload / prefetch ordering is removed.** `FeedPlayback.kt` no longer uses the
  Media3 1.5+ `DefaultPreloadManager` / `TargetPreloadStatusControl`. It is
  rewritten around `ExoPlayer.Builder` plus a
  `linkedMapOf<String, MediaSource>()` that reuses already-built `MediaSource`s;
  `canPrefetch()` depends on `totalBufferedDuration` and `setCurrentIndex()` is now
  a no-op. This reduces KitKat TV memory pressure at the cost of no pre-buffering
  or preload ordering.

## Update and release transition

Release metadata and scripts on this branch declare minSdk 19; the APK filename is
`hongguotv-kotlin-<version>-android4.apk`. The client-side parser still accepts the
historical `android5.apk` and `android8.apk` filenames.

- Accept minSdk 19 update metadata and the `android4.apk` filename; malformed
  filenames are still rejected.
- Old 0.9 / 0.10 clients do not recognise minSdk 19 metadata and only accept
  historical filenames. The first API 19 release therefore needs a manual
  **same-signature overwrite installation** for those clients.
- Do **not** uninstall first: uninstalling removes local records. Do not advertise
  a minSdk the APK was not built with; a debug APK cannot overwrite the official
  signed APK. Subsequent updates use the new metadata parser.
- See [AUTO_UPDATES.md](AUTO_UPDATES.md) for the release workflow.

## Build and verification requirements

- **JDK 17 is required.** AGP 8.11.0 + Gradle 8.14.3 with Kotlin DSL cannot parse
  the JDK 25 version string `25.0.2` and fail with
  `java.lang.IllegalArgumentException: 25.0.2`. Do not document JDK 25 as the build
  JDK.
- Android SDK 36 platform and build-tools 35.0.0 / 36.0.0 are installed through the
  Tencent Cloud AndroidSDK mirror; the Gradle distribution is served from the
  Huawei Cloud mirror and Robolectric from the Alibaba Cloud mirror.
- Do not suppress `NewApi` and do not override dependency minSdk errors to force a
  build. Inspect the merged manifest for minSdk 19 and the APK for `lib/*/*.so`.
- **Verification status (do not report as passed).** `:core:test` built successfully
  on this branch after the OkHttp getter migration. `:app:testDebugUnitTest`,
  `:app:lintDebug` and `:app:assembleDebug` are still being verified by the main
  agent and their results are not final. No real Android 4.4.2 TV has been tested.

## Required real-device acceptance checklist

Group runs by API level; source/build compatibility is not a substitute for these.
Do not solve HTTPS certificate failures on old vendor firmware by disabling TLS
verification.

**API 19 (Android 4.4 / 4.4.2) — primary target**

1. Install the `android4.apk` with the original signature over a same-signature
   install; confirm cold start with MultiDex, browse posters, open settings and
   restart with saved settings.
2. Confirm TLS 1.2 handshakes and the old-system certificate chain against the real
   API/CDN; confirm no certificate verification was disabled.
3. Play a sample with both video and audio using the **platform** decoder (FFmpeg
   JNI is unavailable); pause, seek, change episodes.
4. Disconnect/reconnect Wi-Fi or Ethernet and confirm recovery with no callback
   leak (network callbacks are a no-op on API 19).
5. Phone input and backup/restore QR codes on LAN via the `NetworkInterface` path;
   reject cellular-only/VPN-only endpoints.
6. Check/download a newer signed APK, reject bad checksum/signature/version/SDK
   metadata, and confirm records survive overwrite installation.
7. Remote control / focus behaviour; memory use and MultiDex cold-start time.

**API 21 / 22**

1. Install, cold launch, settings persistence and playback with audio.
2. Network-request callback recovery and no callback leak after exit.
3. LAN address path; `registerNetworkCallback(NetworkRequest...)` branch behaviour.
4. Overwrite installation, update metadata checks and the system unknown-sources
   install flow.

**API 23**

1. Same as API 21/22, plus the `activeNetwork`-related paths where applicable.

**API 24 / 25**

1. Same as above, plus `registerDefaultNetworkCallback` and
   `ApplicationInfo.minSdkVersion` matching update metadata.
2. System unknown-sources install flow (per-app install permission starts at API 26).

**API 26+**

1. Same as above, plus deny-then-grant per-app install permission.
2. Confirm unchanged behaviour on modern firmware.

**All groups**

- A 32-bit ARM device and a 64-bit ARM device, including actual chipset decoder
  behaviour (hardware codecs are separate from the APK's JNI ABI content).
- No device or emulator run is recorded by this compatibility change alone; final
  build, test and real-device results must be supplied by the final integrated
  release report.

See [README.md](README.md) for build setup and [MAIN_INTEGRATION.md](MAIN_INTEGRATION.md)
for the historical main-line integration record, whose figures do not apply to this
branch.
