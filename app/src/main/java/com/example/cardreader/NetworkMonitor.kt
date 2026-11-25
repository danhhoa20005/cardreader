package com.example.cardreader

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Shared network availability monitor that deduplicates callback transitions
 * and optionally allows LAN-only Wi-Fi/Ethernet connectivity for local servers.
 */
class NetworkMonitor(
    context: Context,
    private val allowLanWithoutInternet: Boolean = true,
    private val onStateChanged: (isConnected: Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastReportedState: Boolean? = null

    private val pendingStateCheck = Runnable {
        val current = hasNetworkAccess(appContext, allowLanWithoutInternet)
        if (lastReportedState != current) {
            lastReportedState = current
            onStateChanged(current)
        }
    }

    fun isCurrentlyConnected(): Boolean = hasNetworkAccess(appContext, allowLanWithoutInternet)

    fun register() {
        if (callback != null) return
        lastReportedState = isCurrentlyConnected()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleCheck(delayMs = 0L)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                scheduleCheck(delayMs = 0L)
            }

            override fun onLost(network: Network) {
                // Trì hoãn ngắn để chuyển giao Wi-Fi <-> Ethernet/4G không gây thoát màn hình giả
                scheduleCheck(delayMs = 1200L)
            }
        }
        callback = cb

        val request = NetworkRequest.Builder().build()
        try {
            connectivityManager.registerNetworkCallback(request, cb)
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { connectivityManager.registerDefaultNetworkCallback(cb) }
            }
        }
    }

    fun unregister() {
        mainHandler.removeCallbacks(pendingStateCheck)
        val cb = callback ?: return
        callback = null
        try {
            connectivityManager.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        }
    }

    private fun scheduleCheck(delayMs: Long) {
        mainHandler.removeCallbacks(pendingStateCheck)
        if (delayMs <= 0L) {
            mainHandler.post(pendingStateCheck)
        } else {
            mainHandler.postDelayed(pendingStateCheck, delayMs)
        }
    }

    companion object {
        fun hasNetworkAccess(
            context: Context,
            allowLanWithoutInternet: Boolean = true
        ): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork
            if (isNetworkUsable(cm, active, allowLanWithoutInternet)) return true

            @Suppress("DEPRECATION")
            val networks = cm.allNetworks
            return networks.any { isNetworkUsable(cm, it, allowLanWithoutInternet) }
        }

        fun hasInternetAccess(context: Context): Boolean =
            hasNetworkAccess(context, allowLanWithoutInternet = false)

        private fun isNetworkUsable(
            cm: ConnectivityManager,
            network: Network?,
            allowLanWithoutInternet: Boolean
        ): Boolean {
            if (network == null) return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true
            if (!allowLanWithoutInternet) return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }
}
