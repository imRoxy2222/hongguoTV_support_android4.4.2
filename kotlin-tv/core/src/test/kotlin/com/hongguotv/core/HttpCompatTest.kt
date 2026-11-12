package com.hongguotv.core

import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class HttpCompatTest {
    /** Shaped like the app's TLS 1.2 patch: a delegating wrapper with no platform internals. */
    private class WrappedFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
        override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            delegate.createSocket(socket, host, port, autoClose)
        override fun createSocket(host: String, port: Int): Socket = delegate.createSocket(host, port)
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            delegate.createSocket(host, port, localHost, localPort)
        override fun createSocket(host: InetAddress, port: Int): Socket = delegate.createSocket(host, port)
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            delegate.createSocket(address, port, localAddress, localPort)
    }

    private fun systemTrustManager(): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    @After
    fun resetInstalledPatch() = HttpCompat.clearTlsSocketFactory()

    @Test
    fun aWrappedSocketFactoryIsAcceptedWhenItsTrustManagerIsInstalledToo() {
        val trustManager = systemTrustManager()
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustManager), null)
        val factory = WrappedFactory(context.socketFactory)

        HttpCompat.installTlsSocketFactory(factory, trustManager)

        // Regression: passing the factory alone made OkHttp try to recover the trust manager
        // by reflection, which fails for a wrapper and aborted the first client with
        // IllegalStateException("Unable to extract the trust manager ...").
        assertSame(factory, HttpCompat.newClient().sslSocketFactory())
    }

    @Test
    fun clientsBuildWithoutThePatch() {
        HttpCompat.clearTlsSocketFactory()
        HttpCompat.newClient()
    }
}
