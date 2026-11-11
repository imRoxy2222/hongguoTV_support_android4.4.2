// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

/** Network helpers with a dedicated Android 4.4 (API 19) path. */
internal object NetworkCompat {
    fun manager(context: Context): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun register(manager: ConnectivityManager, callback: ConnectivityManager.NetworkCallback) {
        when {
            Build.VERSION.SDK_INT >= 24 -> manager.registerDefaultNetworkCallback(callback)
            Build.VERSION.SDK_INT >= 21 ->
                manager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback,
                )
            // NetworkCallback, NetworkRequest and NetworkCapabilities do not
            // exist before API 21. Callers must not instantiate a callback there.
            else -> Unit
        }
    }

    @Suppress("DEPRECATION")
    fun available(manager: ConnectivityManager): Boolean =
        if (Build.VERSION.SDK_INT >= 23) manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        else manager.activeNetworkInfo?.isConnected == true

    @Suppress("DEPRECATION")
    private fun active(manager: ConnectivityManager): Network? {
        if (Build.VERSION.SDK_INT >= 23) return manager.activeNetwork
        val current = manager.activeNetworkInfo?.takeIf { it.isConnected } ?: return null
        return manager.allNetworks.firstOrNull { network ->
            manager.getNetworkInfo(network)?.let { it.isConnected && it.type == current.type } == true
        }
    }

    /** Never offer a cellular/VPN address as a same-LAN pairing endpoint. */
    fun lanAddress(manager: ConnectivityManager): String? {
        if (Build.VERSION.SDK_INT < 21) return legacyLanAddress()
        val network = active(manager) ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null
        return manager.getLinkProperties(network)?.linkAddresses?.map { it.address }
            ?.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }

    /** API 19 has no Network/NetworkCapabilities; enumerate interfaces directly. */
    private fun legacyLanAddress(): String? =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { iface ->
                iface.isUp &&
                    !iface.isLoopback &&
                    Regex("^(wlan|wifi|wlp|eth|en)[0-9a-z_.-]*$").matches(iface.name)
            }
            .flatMap { iface -> iface.inetAddresses.toList() }
            .firstOrNull { address ->
                address is Inet4Address &&
                    address.isSiteLocalAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isLoopbackAddress
            }
            ?.hostAddress
}
