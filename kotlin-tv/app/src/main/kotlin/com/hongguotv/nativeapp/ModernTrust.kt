// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.util.Log
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * KitKat's system trust store is frozen at the 2013 CA set, so public roots issued after it
 * (DigiCert Global Root G2, ISRG Root X1, GlobalSign R3/R6, Amazon Root CA 1-4, ...) are absent.
 * Media and API hosts behind those roots then fail with
 * `SSLHandshakeException: Trust anchor for certification path not found`, which on this branch
 * shows up as "播放设备注册失败" before any playback request is attempted.
 *
 * The APK therefore ships a current public CA bundle as a **supplement** to the system anchors:
 * the system trust manager always decides first, and the bundled roots are consulted only when
 * the system rejects the chain. Chain building, validity periods and hostname verification stay
 * in place — nothing is bypassed, and no private or self-signed anchor is trusted.
 */
internal object ModernTrust {
    private const val TAG = "HongguoTV"

    /** System anchors first, bundled public roots as a fallback. Null when the bundle is unusable. */
    fun supplement(context: Context): X509TrustManager? =
        runCatching {
            SupplementTrustManager(
                system = trustManager(null) ?: return null,
                bundled = trustManager(loadBundle(context)) ?: return null,
            )
        }.onFailure { Log.w(TAG, "bundled CA anchors unavailable", it) }
            .getOrNull()

    private fun trustManager(store: KeyStore?): X509TrustManager? =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(store) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()

    private fun loadBundle(context: Context): KeyStore? {
        val certificates =
            context.resources.openRawResource(R.raw.ca_bundle).use { input ->
                CertificateFactory.getInstance("X.509")
                    .generateCertificates(input)
                    .filterIsInstance<X509Certificate>()
            }
        if (certificates.isEmpty()) return null
        return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            certificates.forEachIndexed { index, certificate ->
                setCertificateEntry("bundle-$index", certificate)
            }
        }
    }

    private class SupplementTrustManager(
        private val system: X509TrustManager,
        private val bundled: X509TrustManager,
    ) : X509TrustManager {
        // Client certificates are never requested by these hosts; keep the platform behaviour.
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            system.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (systemRejection: CertificateException) {
                try {
                    bundled.checkServerTrusted(chain, authType)
                } catch (bundledRejection: CertificateException) {
                    // Surface the system verdict so failures still name the missing anchor.
                    throw systemRejection
                }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            system.acceptedIssuers + bundled.acceptedIssuers
    }
}
