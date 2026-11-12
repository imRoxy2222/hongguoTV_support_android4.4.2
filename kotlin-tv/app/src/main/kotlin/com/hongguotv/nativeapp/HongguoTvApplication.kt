// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.os.Build
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.hongguotv.core.HttpCompat
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Installs API 19 compatibility hooks before any network client is created. */
class HongguoTvApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT < 21) enableTls12(this)
    }
}

/**
 * Android 4.4 ships an OpenSSL build that supports TLS 1.2, but the platform
 * enables only TLS 1.0 on newly created sockets. Modern API and CDN hosts
 * reject TLS 1.0, so explicitly enable the strongest mutually supported
 * protocol on every socket.
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
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), null)
        HttpCompat.installTlsSocketFactory(Tls12SocketFactory(context.socketFactory), trustManager)
        if (BuildConfig.DEBUG) Log.i("HongguoTV", "TLS 1.2 + ${if (supplement == null) "system" else "supplemented"} trust store installed")
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
