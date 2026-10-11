package com.foodcheck.data.datasources

import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.data.local.AppFiles
import com.foodcheck.data.network.BackendConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PendingStanceMutation(
    val id: Int,
    val stance: String,
    val enqueuedAtEpochMs: Long,
)

@Serializable
data class ShelfCacheSnapshot(
    val deviceKey: String,
    val items: List<ShelfItemDto> = emptyList(),
    val pendingStance: List<PendingStanceMutation> = emptyList(),
    val syncedAtEpochMs: Long = 0L,
)

class ShelfLocalStore(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val mutex = Mutex()
    private val fileName = "shelf_cache.json"

    suspend fun load(): ShelfCacheSnapshot? = mutex.withLock {
        val raw = AppFiles.readUtf8(fileName) ?: return@withLock null
        runCatching { json.decodeFromString(ShelfCacheSnapshot.serializer(), raw) }.getOrNull()
            ?.takeIf { it.deviceKey == BackendConfig.ensureDeviceKey() }
    }

    suspend fun save(snapshot: ShelfCacheSnapshot) = mutex.withLock {
        val keyed = snapshot.copy(deviceKey = BackendConfig.ensureDeviceKey())
        AppFiles.writeUtf8(fileName, json.encodeToString(ShelfCacheSnapshot.serializer(), keyed))
    }

    suspend fun clear() = mutex.withLock {
        AppFiles.writeUtf8(fileName, "")
    }
}
