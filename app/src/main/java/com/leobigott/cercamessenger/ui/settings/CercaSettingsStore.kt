package com.leobigott.cercamessenger.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CercaSettingsStore {
    private const val PREFS = "cerca_settings"

    private const val KEY_HEARTBEAT_SECONDS = "heartbeat_seconds"
    private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    private const val KEY_CLOUD_SYNC_SECONDS = "cloud_sync_seconds"

    val allowedHeartbeatSeconds = listOf(15, 30, 60, 120)
    val allowedCloudSyncSeconds = listOf(15, 30, 60, 120)

    private val _heartbeatSeconds = MutableStateFlow(15)
    val heartbeatSeconds: StateFlow<Int> = _heartbeatSeconds

    private val _cloudSyncEnabled = MutableStateFlow(true)
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled

    private val _cloudSyncSeconds = MutableStateFlow(15)
    val cloudSyncSeconds: StateFlow<Int> = _cloudSyncSeconds

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val heartbeatValue = prefs
            .getInt(KEY_HEARTBEAT_SECONDS, 15)
            .takeIf { it in allowedHeartbeatSeconds }
            ?: 15

        val cloudEnabledValue = prefs
            .getBoolean(KEY_CLOUD_SYNC_ENABLED, true)

        val cloudSecondsValue = prefs
            .getInt(KEY_CLOUD_SYNC_SECONDS, 15)
            .takeIf { it in allowedCloudSyncSeconds }
            ?: 15

        _heartbeatSeconds.value = heartbeatValue
        _cloudSyncEnabled.value = cloudEnabledValue
        _cloudSyncSeconds.value = cloudSecondsValue
    }

    fun setHeartbeatSeconds(context: Context, seconds: Int) {
        val safe = seconds.takeIf { it in allowedHeartbeatSeconds } ?: 15

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HEARTBEAT_SECONDS, safe)
            .apply()

        _heartbeatSeconds.value = safe
    }

    fun setCloudSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CLOUD_SYNC_ENABLED, enabled)
            .apply()

        _cloudSyncEnabled.value = enabled
    }

    fun setCloudSyncSeconds(context: Context, seconds: Int) {
        val safe = seconds.takeIf { it in allowedCloudSyncSeconds } ?: 15

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CLOUD_SYNC_SECONDS, safe)
            .apply()

        _cloudSyncSeconds.value = safe
    }
}