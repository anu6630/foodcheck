package com.foodcheck.data.repositories

import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.datasources.PendingStanceMutation
import com.foodcheck.data.datasources.ShelfCacheSnapshot
import com.foodcheck.data.datasources.ShelfLocalStore
import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.data.network.BackendConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Offline-first shelf: local snapshot is source of truth for UI;
 * network pull/push keeps it fresh. Stance edits are optimistic + queued.
 */
class ShelfRepository(
    private val network: NetworkDataSource,
    private val local: ShelfLocalStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _items = MutableStateFlow<List<ShelfItemDto>>(emptyList())
    val items: StateFlow<List<ShelfItemDto>> = _items.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingStanceMutation>>(emptyList())
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        scope.launch { hydrateFromDisk() }
    }

    fun filtered(query: String): List<ShelfItemDto> {
        val q = query.trim()
        if (q.isEmpty()) return _items.value
        return _items.value.filter { item ->
            item.displayName.contains(q, ignoreCase = true) ||
                (item.brand?.contains(q, ignoreCase = true) == true) ||
                item.tags.any { it.contains(q, ignoreCase = true) } ||
                item.companies.any { it.name.contains(q, ignoreCase = true) } ||
                item.ingredients.any { it.name.contains(q, ignoreCase = true) }
        }
    }

    suspend fun hydrateFromDisk() {
        val snap = local.load() ?: return
        mutex.withLock {
            _pending.value = snap.pendingStance
            _items.value = applyPending(snap.items, snap.pendingStance)
        }
    }

    private val refreshMutex = Mutex()

    /** Stale-while-revalidate: keep local UI, then pull remote and merge. */
    suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        _syncing.value = true
        try {
            flushPending()
            val remote = network.listShelf("").items
            persistAndPublish(remote)
            _lastError.value = null
        } catch (e: Exception) {
            if (_items.value.isEmpty()) {
                _lastError.value = e.message ?: "Could not load list"
            } else {
                // Keep showing local; soft error.
                _lastError.value = null
            }
        } finally {
            _syncing.value = false
            refreshMutex.unlock()
        }
    }

    suspend fun updateStance(id: Int, stance: String) {
        val mutation = PendingStanceMutation(
            id = id,
            stance = stance,
            enqueuedAtEpochMs = nowMs(),
        )
        mutex.withLock {
            val pending = _pending.value.filterNot { it.id == id } + mutation
            _pending.value = pending
            _items.value = _items.value.map {
                if (it.id == id) it.copy(stance = stance) else it
            }
            persistCurrent()
        }
        try {
            network.updateShelfStance(id, stance)
            mutex.withLock {
                _pending.value = _pending.value.filterNot { it.id == id && it.stance == stance }
                persistCurrent()
            }
            _lastError.value = null
        } catch (e: Exception) {
            _lastError.value = e.message ?: "Could not update stance"
        }
    }

    /** After a successful create on the server, pull fresh list into cache. */
    suspend fun invalidateAndRefresh() {
        refresh()
    }

    private suspend fun flushPending() {
        val pending = mutex.withLock { _pending.value.toList() }
        if (pending.isEmpty()) return
        val remaining = mutableListOf<PendingStanceMutation>()
        for (m in pending) {
            try {
                network.updateShelfStance(m.id, m.stance)
            } catch (_: Exception) {
                remaining += m
            }
        }
        mutex.withLock {
            _pending.value = remaining
            persistCurrent()
        }
    }

    private suspend fun persistAndPublish(remoteItems: List<ShelfItemDto>) {
        mutex.withLock {
            val pending = _pending.value
            val merged = applyPending(remoteItems, pending)
            _items.value = merged
            local.save(
                ShelfCacheSnapshot(
                    deviceKey = BackendConfig.ensureDeviceKey(),
                    items = remoteItems,
                    pendingStance = pending,
                    syncedAtEpochMs = nowMs(),
                ),
            )
        }
    }

    private suspend fun persistCurrent() {
        local.save(
            ShelfCacheSnapshot(
                deviceKey = BackendConfig.ensureDeviceKey(),
                items = _items.value,
                pendingStance = _pending.value,
                syncedAtEpochMs = nowMs(),
            ),
        )
    }

    private fun applyPending(
        items: List<ShelfItemDto>,
        pending: List<PendingStanceMutation>,
    ): List<ShelfItemDto> {
        if (pending.isEmpty()) return items
        val byId = pending.associateBy { it.id }
        return items.map { item ->
            val m = byId[item.id] ?: return@map item
            item.copy(stance = m.stance)
        }
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
