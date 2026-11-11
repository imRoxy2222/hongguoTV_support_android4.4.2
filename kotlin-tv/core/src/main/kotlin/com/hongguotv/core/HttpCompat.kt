// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.core

import okhttp3.OkHttpClient
import javax.net.ssl.SSLSocketFactory

/**
 * Central HTTP client factory for the Android 4.4 (API 19) build.
 *
 * Android 4.4 supports TLS 1.2 but leaves only TLS 1.0 enabled on new sockets.
 * The app installs a patched SSLSocketFactory in Application.onCreate on
 * API < 21; every production OkHttpClient must be created through this factory
 * so the patch is applied. JVM unit tests install nothing and keep using plain
 * OkHttp clients.
 */
object HttpCompat {
    @Volatile
    private var tlsSocketFactory: SSLSocketFactory? = null

    fun installTlsSocketFactory(factory: SSLSocketFactory) {
        tlsSocketFactory = factory
    }

    fun newBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder().apply {
            tlsSocketFactory?.let { sslSocketFactory(it) }
        }

    fun newClient(): OkHttpClient = newBuilder().build()
}
