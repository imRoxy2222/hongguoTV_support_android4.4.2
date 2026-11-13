# Android 4.4 / 4.4.2 (API 19) compatibility

This branch lowers the minimum SDK of the native APK from API 21 (Android 5.0) to
API 19 (Android 4.4.2 / KitKat). `targetSdk` is now **19** as well, so the app runs
entirely on KitKat-era platform behaviour; `compileSdk` stays 36 so modern build
tooling and lint still apply. The package name and the original signing
configuration are preserved; releases follow the `<upstream>-<N>` version rule in
[docs/RELEASE_POLICY.md](../docs/RELEASE_POLICY.md).

The first real-device run on a MGV2000-UAV5_GD (Android 4.4.2, armeabi-v7a,
1280x720, 384 MB heap) is recorded under "Real-device verification" below. It found
and fixed the one blocking defect: KitKat's system trust store predates the CA that
the media API chains to.

## Scope and boundaries

- Supported floor: API 19. Devices below API 19 are not a target of this branch.
- The manifest now uses a custom `.HongguoTvApplication`, a subclass of
  `androidx.multidex.MultiDexApplication`, instead of the library class directly.
- `targetSdk` is 19: on every system the app keeps install-time permission grants,
  cleartext HTTP, legacy file access and no high-targetSdk compatibility shims that
  vendor TV firmwares may impose. `compileSdk` 36 is kept for tooling and lint;
  API 19 behaviour is handled at runtime by SDK branches.
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
  `TLSv1.2`, `TLSv1.1`, `TLSv1` in order on each `SSLSocket`.
- **Bundled public CA supplement (`ModernTrust.kt`).** KitKat's system trust store is
  the 2013 CA set. The media API chains to `RapidSSL TLS RSA CA G1` rooted at
  **DigiCert Global Root G2**, which KitKat does not ship, so every HTTPS call failed
  with `SSLHandshakeException: Trust anchor for certification path not found` and the
  home screen reported 播放设备注册失败. `res/raw/ca_bundle.pem` (148 current public
  roots, 217 KB) is loaded into a in-memory `KeyStore` and wrapped in a
  `SupplementTrustManager`: the platform manager always decides first, and the
  bundled roots are consulted **only** when the platform rejects the chain. If both
  reject, the platform's `CertificateException` is rethrown. Chain building, validity
  windows and hostname verification are unchanged; no private, self-signed or pinned
  anchor is trusted.
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
- **System media session is unavailable on KitKat.** `android.media.session.*`
  only exists from API 21. `MainActivity` keeps a nullable `TvMediaSession`, never
  constructs it on API 19, and guards every call; the class is annotated
  `@RequiresApi(21)`. Remote/now-playing integration is therefore absent on
  Android 4.4.2, while in-app controls still work.
- **Video-size pre-check is weaker on KitKat.** `MediaCodecInfo.VideoCapabilities`
  is API 21+. On API 19 the decoder probe can only verify that a decoder declares
  the MIME type; runtime decode failures remain covered by the persisted
  learned-failure table. Decoder enumeration uses the legacy indexed
  `MediaCodecList` API reflectively, because its no-arg constructor is no longer in
  the compileSdk 36 stubs.
- **Cosmetic tinting and Material theme are API 21+ only.** Progress-bar, button
  and background tint calls are guarded by `SDK_INT >= 21`. `res/values/styles.xml`
  uses a Holo baseline (Material does not exist on KitKat) and
  `res/values-v21/styles.xml` restores the original Material parents and
  `android:colorAccent` on newer systems. `TextView.showSoftInputOnFocus` is reached
  reflectively on KitKat via its hidden method names.
- **PackageInstaller is unavailable on KitKat.** `UpdateInstallSession.submit()`
  rejects API < 21 explicitly; the app falls back to the intent / file-manager
  update path, and the result activity guards `abandonSession()`.

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

## Upstream 0.14.0 sync (2026-11-13)

`main` was fast-forwarded to upstream `21ec922` (0.14.0) and merged into this
branch. Upstream 0.14.0 adds the LAN phone console (`PhoneControlServer`,
`PhoneControlPage`, `PhoneConsole`, `PhoneControlCatalog`), a separate TV search
page, library/filter rework, phone-login rate limiting and the pixel-TV branding.
All of those features were kept, with the following API 19 adaptations:

- **OkHttp 3.12 / Okio 1.x getters.** New upstream code used OkHttp 4.x Kotlin
  properties (`url.scheme`, `response.body`, `request.method`, `http.dispatcher`,
  `request.url.encodedPath`) and the `ResponseBody.Companion.toResponseBody`
  extension. They were migrated to the Java getters and to
  `com.hongguotv.core.compat.toResponseBody`; `Source.buffer()` became
  `okio.Okio.buffer(source)`. Affected: `WebLogin.authorizationAppUrl`,
  `PhoneConsole`, `PhoneLoginTest`, `PhoneConsoleTest`, `InteractionFlowTest`,
  `AccountFavoriteActionsTest`, `AccountReservationsTest`.
- **Launcher icon.** `AndroidManifest.xml` adopts upstream `@mipmap/ic_launcher`
  and the `${appLabel}` placeholder while keeping the mandatory
  `.HongguoTvApplication` (MultiDex + TLS 1.2 + bundled CA supplement).
  `mipmap-anydpi-v26` adaptive icons are ignored on KitKat; density-specific PNGs
  are used instead.
- **Theme.** Upstream's brighter palette (`#202B3B`, `#C0CCDE`) is applied to both
  `values/styles.xml` (Holo baseline) and `values-v21/styles.xml`; the API 21-only
  `android:colorAccent` stays out of the KitKat baseline.
- **Build config.** `minSdk` / `targetSdk` 19, `multiDexEnabled`, the universal-APK
  release contract and the `<upstream>-<N>` version rule are unchanged; upstream's
  `appLabel` placeholder and debug `applicationIdSuffix` were adopted.
- **Release contract.** Offline tests and `apksigner --min-sdk-version` now pass
  the real SDK (19) instead of the upstream hard-coded 21.

Post-merge verification on JDK 17: `:core:test`, `:app:testDebugUnitTest`,
`:app:lintRelease` and the offline `test_release_contract.py` suite all pass. The
source version still reads `0.13.2-1` / 1701 — bump it with
`python3 scripts/next_version.py --write` (expected `0.14.0-1` / 1801) and update
`test_current_source_version` before tagging a release.

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
  The single lint exemption in `app/build.gradle.kts` is
  `ExpiredTargetSdkVersion`, which only encodes the Google Play requirement
  (`targetSdk` ≥ 33); this branch distributes by sideload to Android 4.4.2 TVs, so
  the deliberate `targetSdk 19` is not a defect.
- **Verification status (host-side build checks only).** On this branch,
  `:core:test` (335 tests), `:app:testDebugUnitTest` (311 tests),
  `:app:lintRelease` (0 errors, 95 warnings) and `:app:assembleDebug` all completed
  successfully. The merged debug manifest declares `minSdkVersion="19"` /
  `targetSdkVersion="19"`; the debug APK contains four dex files (MultiDex active),
  `res/raw/ca_bundle.pem`, and no `lib/*.so`, consistent with the disabled native
  FFmpeg build. API 21+ calls are gated by `Build.VERSION.SDK_INT` branches or
  isolated in `@RequiresApi(21)` holders — no project-wide `NewApi` suppression or
  lint baseline is used. View decorations that KitKat's `View` lacks outright
  (`clipToOutline`, `elevation`, `translationZ`) are routed through
  `Api21Compat`, so a call site cannot forget the version branch.
- **Run `:app:lintRelease` before every release tag.** Upstream 0.13.2 added nine
  direct API 21 view-property calls to `HomeFeedScreen`; they compile against
  `compileSdk 36`, pass `:app:testDebugUnitTest` and `:app:assembleDebug`, and throw
  `NoSuchMethodError` on a real API 19 device. Lint is the only host-side check that
  catches this class of upstream regression, which is why it must stay on the
  release path rather than being replaced by a baseline.
- **Robolectric cannot emulate API 19.** Robolectric 4.14.1 `DefaultSdkProvider`
  starts at `5.0.2_r3` (API 21); `@Config(sdk = [19])` fails with `API level 19 is
  not available`. The compatibility matrices therefore run on API 21/23/26, while
  `Api19BranchTest` forces `Build.VERSION.SDK_INT` to 19 inside an API 21 sandbox
  to exercise the KitKat-only branches. This is branch coverage, not device
  verification.

## Real-device verification

One physical device has been exercised over ADB (2026-11-12):

| Property | Value |
| --- | --- |
| Model / firmware | `MGV2000-UAV5_GD`, TCL/移动定制, `p201_iptv` |
| Android | 4.4.2 (SDK 19), armeabi-v7a |
| Display | 1280x720 @ 160 dpi |
| Dalvik heap | 384 MB |
| Install | `pm install -r` of a chunked `adb push` (see `own_script/adb_push_chunked.sh`); the device's adbd resets long transfers |

Observed results:

- Cold start: MultiDex extracts four secondary dex files and dex2oat compiles them on
  first launch after an install (~40 s once); later launches reuse them.
- Device registration, home recommendations, detail, play-address and first frame all
  succeed after the CA supplement (first frame 0.5–1.7 s). Before the supplement the
  home screen showed 播放设备注册失败 with
  `SSLHandshakeException: Trust anchor for certification path not found`.
- Full-screen playback on this Amlogic box occupies the video layer, so
  `adb shell screencap` blocks while a video is playing. Screenshots are only
  reliable on UI screens. `own_script/adb_retry.sh` wraps idempotent ADB calls with
  reconnect-and-retry because the device's adbd drops connections under load.
- Not yet exercised on this device: account login, comments, relay/伴机 pairing,
  auto-update install flow, and the bytevc2 fallback path.

## Required real-device acceptance checklist

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
