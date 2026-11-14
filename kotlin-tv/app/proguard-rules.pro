-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn javax.mail.**
-dontwarn javax.activation.**

# Conscrypt bundles BoringSSL for TLS 1.2 on API 19. Its classes are reached
# through JNI and reflection, and the two platform adapters below reference
# classes that only exist on some Android versions (KitKat's harmony provider
# vs. the AndroidOpenSSL provider added in API 21), so they are optional.
-keep class org.conscrypt.** { *; }
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
