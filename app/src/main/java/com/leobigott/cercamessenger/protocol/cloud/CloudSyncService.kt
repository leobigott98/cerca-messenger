package com.leobigott.cercamessenger.protocol.cloud

interface CloudSyncService {
    suspend fun syncNow()
    suspend fun uploadOnly()
    suspend fun downloadNow()
}

class NoOpCloudSyncService : CloudSyncService {
    override suspend fun syncNow() = Unit
    override suspend fun uploadOnly() = Unit
    override suspend fun downloadNow() = Unit
}
