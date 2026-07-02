package com.leobigott.cercamessenger.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CercaSettingsStore {
    private const val PREFS = "cerca_settings"
    private const val KEY_HEARTBEAT_SECONDS = "heartbeat_seconds"

    val allowedHeartbeatSeconds = listOf(5, 15, 30, 60)

    private val _heartbeatSeconds = MutableStateFlow(5)
    val heartbeatSeconds: StateFlow<Int> = _heartbeatSeconds

    fun load(context: Context) {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_HEARTBEAT_SECONDS, 5)
            .takeIf { it in allowedHeartbeatSeconds }
            ?: 5

        _heartbeatSeconds.value = value
    }

    fun setHeartbeatSeconds(context: Context, seconds: Int) {
        val safe = seconds.takeIf { it in allowedHeartbeatSeconds } ?: 5

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HEARTBEAT_SECONDS, safe)
            .apply()

        _heartbeatSeconds.value = safe
    }
}