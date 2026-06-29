package com.leobigott.cercamessenger.protocol.cloud

interface CloudSyncService {
    suspend fun syncNow()
}

class NoOpCloudSyncService : CloudSyncService {
    override suspend fun syncNow() = Unit
}
