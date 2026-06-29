package com.leobigott.cercamessenger.protocol

import android.content.Context
import android.os.Build
import com.leobigott.cercamessenger.data.local.CercaDatabase
import com.leobigott.cercamessenger.protocol.nearby.NearbyProtocolEngine
import com.leobigott.cercamessenger.protocol.cloud.FirebaseCloudSyncService

object ProtocolEngineProvider {
    @Volatile
    private var engineInstance: ProtocolEngine? = null

    fun init(context: Context) {
        if (engineInstance != null) return
        synchronized(this) {
            if (engineInstance == null) {
                val appContext = context.applicationContext
                val database = CercaDatabase.getInstance(appContext)
                val nodeId = DeviceIdentityStore.getOrCreateNodeId(appContext)
                engineInstance = NearbyProtocolEngine(
                    context = appContext,
                    database = database,
                    localNodeId = nodeId,
                    displayName = "${Build.MODEL ?: "CERCA Android"}|$nodeId",
                    cloudSyncService = FirebaseCloudSyncService(database, nodeId)
                )
            }
        }
    }

    val engine: ProtocolEngine
        get() = requireNotNull(engineInstance) {
            "ProtocolEngineProvider.init(context) must be called before using the engine."
        }
}
