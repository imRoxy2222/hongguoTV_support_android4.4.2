// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.core

import okhttp3.OkHttpClient
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Central HTTP client factory for the Android 4.4 (API 19) build.
 *
 * Android 4.4 supports TLS 1.2 but leaves only TLS 1.0 enabled on new sockets.
 * The app installs a patched SSLSocketFactory in Application.onCreate on
 * API < 21; every production OkHttpClient must be created through this factory
 * so the patch is applied. JVM unit tests install nothing and keep using plain
 * OkHttp clients.
 *
 * The trust manager is carried next to the factory on purpose. OkHttp's
 * single-argument `sslSocketFactory(factory)` asks the platform to dig the trust
 * manager out of the factory object by reflection: Android reads an
 * `sslParameters` field first and then falls back to the JVM-only
 * `sun.security.ssl.SSLContextImpl`. Our patched factory is a delegating wrapper
 * with no platform internals, so that lookup finds nothing, returns null, and the
 * builder throws `IllegalStateException: Unable to extract the trust manager`
 * while the client is still being constructed. Always install the factory
 * together with the trust manager it was created from.
 */
object HttpCompat {
    private class Tls(
        val socketFactory: SSLSocketFactory,
        val trustManager: X509TrustManager,
    )

    @Volatile
    private var tls: Tls? = null

    fun installTlsSocketFactory(factory: SSLSocketFactory, trustManager: X509TrustManager) {
        tls = Tls(factory, trustManager)
    }

    /** Test-only: drop the installed patch so one JVM test cannot leak into the next. */
    internal fun clearTlsSocketFactory() {
        tls = null
    }

    fun newBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder().apply {
            tls?.let { sslSocketFactory(it.socketFactory, it.trustManager) }
        }

    fun newClient(): OkHttpClient = newBuilder().build()
}
