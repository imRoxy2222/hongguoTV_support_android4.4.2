// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.os.Build
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.hongguotv.core.HttpCompat
import org.conscrypt.Conscrypt
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager

/** Installs API 19 compatibility hooks before any network client is created. */
class HongguoTvApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT < 21) enableTls12(this)
    }
}

/**
 * Android 4.4 ships an OpenSSL build that supports TLS 1.2, but the platform
 * enables only TLS 1.0 on newly created sockets and exposes no AEAD cipher
 * suites at all, so modern hosts refuse the handshake. Conscrypt provides the
 * real TLS 1.2 stack; the platform socket factory is only a fallback for devices
 * where its native library cannot load.
 *
 * The same context also carries the bundled public CA supplement: KitKat's system
 * trust store predates the roots that today's servers chain to, so without it every
 * HTTPS call fails with "Trust anchor for certification path not found". The
 * supplement only adds public roots that the system store is missing — the platform
 * anchors still decide first and certificate verification is never disabled.
 *
 * The trust manager is installed next to the socket factory: the client factory
 * hands both to OkHttp, which cannot recover the trust manager from our wrapper
 * by reflection (see HttpCompat).
 */
private fun enableTls12(application: HongguoTvApplication) {
    runCatching {
        val supplement = ModernTrust.supplement(application)
        val trustManager = supplement ?: ModernTrust.system() ?: return@runCatching
        // KitKat's JSSE offers only CBC suites, while GitHub and every public
        // accelerator now require AEAD, so the handshake is refused. Conscrypt's
        // bundled BoringSSL supplies the missing suites; fall back to the platform
        // stack (with TLS 1.2 enabled) if its native library cannot load.
        val provider = runCatching { Conscrypt.newProvider() }.getOrNull()
        val context = if (provider == null) SSLContext.getInstance("TLS")
            else SSLContext.getInstance("TLS", provider)
        context.init(null, arrayOf<TrustManager>(trustManager), null)
        val factory = if (provider == null) Tls12SocketFactory(context.socketFactory) else context.socketFactory
        HttpCompat.installTlsSocketFactory(factory, trustManager)
        if (BuildConfig.DEBUG) Log.i("HongguoTV", "TLS ${if (provider == null) "1.2" else "1.2/Conscrypt"} + ${if (supplement == null) "system" else "supplemented"} trust store installed")
    }.onFailure { Log.w("HongguoTV", "TLS 1.2 enablement failed", it) }
}

private class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(host: String, port: Int): Socket =
        delegate.createSocket(host, port).patched()

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = delegate.createSocket(host, port, localHost, localPort).patched()

    override fun createSocket(host: InetAddress, port: Int): Socket =
        delegate.createSocket(host, port).patched()

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = delegate.createSocket(address, port, localAddress, localPort).patched()

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        delegate.createSocket(socket, host, port, autoClose).patched()

    private fun Socket.patched(): Socket {
        if (this is SSLSocket) {
            val supported = supportedProtocols?.toSet().orEmpty()
            val preferred = arrayOf("TLSv1.2", "TLSv1.1", "TLSv1").filter { it in supported }
            if (preferred.isNotEmpty()) enabledProtocols = preferred.toTypedArray()
        }
        return this
    }
}
