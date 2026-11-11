plugins { id("com.android.library") }
android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 36
    defaultConfig { minSdk = 19; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    api("androidx.media3:media3-decoder:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    compileOnly("org.checkerframework:checker-qual:3.49.3")
}

// Native FFmpeg audio decoding is disabled for the API 19 build: NDK r27 no longer
// supports API 19 and no older NDK is provisioned. The Java extension classes remain
// compiled, but FfmpegLibrary.isAvailable() stays false, so DefaultRenderersFactory
// falls back to the platform decoder on Android 4.4.2. See ANDROID_COMPATIBILITY.md.
