package com.leobigott.cercamessenger.protocol.nearby

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

class DeviceStatusProvider(
    private val context: Context
) {
    fun batteryRatio(): Double {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return (value / 100.0).coerceIn(0.0, 1.0)
    }

    fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun bufferFreeRatio(): Double {
        // Application-level buffer. For v1 we use a simple default.
        // Later: compute from Room pending-message bytes vs configured max.
        return 1.0
    }
}
