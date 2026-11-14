import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val signingProperties =
    Properties().apply {
        val config = rootProject.file("keystore.properties")
        if (config.exists()) config.inputStream().use { load(it) }
    }

fun signingValue(env: String, key: String): String? =
    System.getenv(env) ?: signingProperties.getProperty(key)

android {
    namespace = "com.hongguotv.nativeapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.hongguotv.nativeapp"
        manifestPlaceholders["appLabel"] = "红果 TV 原生版"
        minSdk = 19
        // ART loads secondary dex files natively only from API 21; KitKat needs MultiDex.
        multiDexEnabled = true
        // This build only targets Android 4.4.2 (API 19) TVs. Declaring targetSdk 19 keeps the
        // whole app on KitKat-era platform behaviour on every system it runs on: install-time
        // permission grant, cleartext HTTP allowed, legacy file access, and no high-targetSdk
        // compatibility shims imposed by vendor firmwares.
        targetSdk = 19
        // 版本规则见 docs/RELEASE_POLICY.md：versionName = <上游版本>-<N>，
        // versionCode = 上游 versionCode * 100 + N，N 是该上游版本的第几次 fork 发布。
        // 由 scripts/next_version.py --write 自动维护，不要手改。
        versionCode = System.getenv("HONGGUOTV_VERSION_CODE")?.toInt() ?: 2101
        versionName = System.getenv("HONGGUOTV_VERSION_NAME") ?: "0.16.0-1"
    }
    // Formal publishing always requests all three installable APKs.
    splits {
        abi {
            isEnable = providers.gradleProperty("releaseAbis").orNull == "true"
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    buildFeatures { buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty(
                "robolectric.dependency.repo.url",
                "https://maven.aliyun.com/repository/public",
            )
        }
    }
    signingConfigs {
        create("delivery") {
            storeFile = signingValue("HONGGUOTV_KEYSTORE", "storeFile")?.let { file(it) }
            storePassword = signingValue("HONGGUOTV_STORE_PASSWORD", "storePassword")
            keyAlias = signingValue("HONGGUOTV_KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("HONGGUOTV_KEY_PASSWORD", "keyPassword")
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug.20261114"
            manifestPlaceholders["appLabel"] = "红果 TV 测试版"
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("delivery")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    lint {
        // targetSdk 19 是本分支的刻意设定（理由见 defaultConfig 上方注释）：本分支
        // 只面向 Android 4.4.2 电视侧载分发，不走 Google Play。该检查判定的是
        // Play 上架政策（targetSdk 需 ≥ 33），与本项目分发方式无关，故关闭。
        // 只关闭这一条；NewApi 等真实兼容性错误仍会阻断构建，不设全局抑制或 baseline。
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/versions/**", "META-INF/*.kotlin_module") }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:3.12.13")
    testImplementation("org.robolectric:robolectric:4.14.1") {
        // API 23 tests use legacy graphics, without the cross-platform native renderer.
        exclude(group = "org.robolectric", module = "nativeruntime-dist-compat")
    }
    implementation("androidx.core:core:1.8.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("com.google.zxing:core:3.5.3")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("com.squareup.okhttp3:okhttp:3.12.13")
    implementation(project(":decoder-ffmpeg"))
    implementation(project(":core")) { exclude(group = "org.json") }
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
