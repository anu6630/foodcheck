package com.foodcheck.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodically pings `/health` on both backend tiers and proactively steers
 * [BackendConfig.activeBaseUrl] back to PRIMARY_URL as soon as it's reachable again, instead of
 * staying pinned on SECONDARY_URL after a past failure - which is all the request-time
 * [installFallbackInterceptor] can do on its own (it only reacts to failures, it never checks
 * whether primary has recovered).
 *
 * Uses its own bare HttpClient (no fallback plugin installed) so health pings never trigger the
 * fallback interceptor's own retry-and-swap logic.
 */
class ApiHealthMonitor {
    private val client = HttpClient()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (isActive) {
                poll()
                delay(HEALTH_POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun poll() {
        if (BackendConfig.customBaseUrl != null) return

        if (isHealthy(BackendConfig.PRIMARY_URL)) {
            BackendConfig.activeBaseUrl = BackendConfig.PRIMARY_URL
            return
        }
        if (isHealthy(BackendConfig.SECONDARY_URL)) {
            BackendConfig.activeBaseUrl = BackendConfig.SECONDARY_URL
        }
    }

    private suspend fun isHealthy(url: String): Boolean = try {
        client.get("$url/health").status.value in 200..299
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val HEALTH_POLL_INTERVAL_MS = 45_000L
    }
}
