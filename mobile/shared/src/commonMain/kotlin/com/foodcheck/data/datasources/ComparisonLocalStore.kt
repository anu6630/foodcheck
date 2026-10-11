package com.foodcheck.data.datasources

import com.foodcheck.data.dto.ComparisonDto
import com.foodcheck.data.local.AppFiles
import com.foodcheck.data.network.BackendConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ComparisonCacheSnapshot(
    val deviceKey: String,
    val items: List<ComparisonDto> = emptyList(),
)

/** Last synced comparison list, so My list › Comparisons opens instantly and offline. */
class ComparisonLocalStore(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val mutex = Mutex()
    private val fileName = "comparisons_cache.json"

    suspend fun load(): List<ComparisonDto>? = mutex.withLock {
        val raw = AppFiles.readUtf8(fileName) ?: return@withLock null
        runCatching { json.decodeFromString(ComparisonCacheSnapshot.serializer(), raw) }.getOrNull()
            ?.takeIf { it.deviceKey == BackendConfig.ensureDeviceKey() }
            ?.items
    }

    suspend fun save(items: List<ComparisonDto>) = mutex.withLock {
        val snap = ComparisonCacheSnapshot(BackendConfig.ensureDeviceKey(), items)
        AppFiles.writeUtf8(fileName, json.encodeToString(ComparisonCacheSnapshot.serializer(), snap))
    }
}
