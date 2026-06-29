package com.leobigott.cercamessenger.protocol.nearby

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.leobigott.cercamessenger.R
import com.leobigott.cercamessenger.protocol.ProtocolEngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.leobigott.cercamessenger.settings.CercaSettingsStore

class NearbyLifecycleService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatStarted = false

    override fun onCreate() {
        super.onCreate()

        ProtocolEngineProvider.init(applicationContext)
        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_cerca)
                .setContentTitle("CERCA activo")
                .setContentText("Buscando teléfonos cercanos y reenviando mensajes.")
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        )

        startHeartbeat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNearbyStarted()

        scope.launch {
            ProtocolEngineProvider.engine.startDiscovery()
            delay(2000)
            ProtocolEngineProvider.engine.startDiscovery()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        runBlocking {
            runCatching { ProtocolEngineProvider.engine.stopDiscovery() }
        }

        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNearbyStarted() {
        scope.launch {
            if (!hasNearbyPermissions()) {
                Log.e(TAG, "Cannot start Nearby: missing permissions")
                return@launch
            }

            runCatching {
                Log.d(TAG, "Ensuring Nearby is started from service")

                ProtocolEngineProvider.engine.startDiscovery()

                Log.d(TAG, "Nearby ensure-start requested")
            }.onFailure { error ->
                Log.e(TAG, "Nearby ensure-start failed: ${error.message}", error)
            }
        }
    }

    private fun startHeartbeat() {
        if (heartbeatStarted) return
        heartbeatStarted = true

        scope.launch {
            while (true) {
                if (hasNearbyPermissions()) {
                    runCatching {
                        Log.d(TAG, "Nearby heartbeat: ensuring discovery is active")
                        ProtocolEngineProvider.engine.refreshNearby()
                    }.onFailure { error ->
                        Log.e(TAG, "Nearby heartbeat failed: ${error.message}", error)
                    }
                } else {
                    Log.e(TAG, "Nearby heartbeat skipped: missing permissions")
                }

                val heartbeatMillis = CercaSettingsStore.heartbeatSeconds.value * 1000L
                delay(heartbeatMillis)
            }
        }
    }

    private fun hasNearbyPermissions(): Boolean {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "CERCA nearby service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "Keeps CERCA discovery active for offline crisis messaging."
            }

            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "CERCA_Service"
        private const val CHANNEL_ID = "cerca_nearby_service_v2"
        private const val NOTIFICATION_ID = 8811
    }
}
