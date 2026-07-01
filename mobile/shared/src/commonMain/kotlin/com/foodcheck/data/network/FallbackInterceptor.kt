package com.foodcheck.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException

/**
 * Global Ktor interceptor: on a 5xx response or connection exception, retries the request once
 * on the alternate backend tier and updates [BackendConfig.activeBaseUrl] to match.
 *
 * This replaces the old per-call `runWithFallback()` helper that used to be duplicated inline
 * for every request in NetworkDataSource - the retry-on-alternate-host logic now lives in one
 * place, at the HttpClient level, so any current or future request automatically gets it.
 *
 * Skipped entirely when a manual [BackendConfig.customBaseUrl] override is active (mirrors the
 * previous behavior of `runWithFallback()`, which returned early for a custom base URL).
 *
 * Because the same [io.ktor.client.request.HttpRequestBuilder] (headers included) is reused for
 * the retry, an `Idempotency-Key` header attached to a write request is automatically carried
 * over to the retry on the other host, making it safe to retry.
 */
fun HttpClient.installFallbackInterceptor() {
    plugin(HttpSend).intercept { request ->
        if (BackendConfig.customBaseUrl != null) {
            return@intercept execute(request)
        }

        val firstHost = BackendConfig.activeBaseUrl

        var firstException: Exception? = null
        val firstCall = try {
            execute(request)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            firstException = e
            null
        }

        val is5xx = firstCall?.response?.status?.value?.let { it in 500..599 } ?: false
        if (firstCall != null && !is5xx) return@intercept firstCall

        val alternateHost = BackendConfig.alternateOf(firstHost)
        val reason = firstException?.message ?: "HTTP ${firstCall?.response?.status?.value}"
        println("Request failed on $firstHost: $reason. Retrying on $alternateHost...")

        val alt = Url(alternateHost)
        request.url.protocol = alt.protocol
        request.url.host = alt.host
        request.url.port = alt.port

        var retryException: Exception? = null
        val retryCall = try {
            execute(request)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            retryException = e
            null
        }

        val retryIs5xx = retryCall?.response?.status?.value?.let { it in 500..599 } ?: false
        if (retryCall != null && !retryIs5xx) {
            BackendConfig.activeBaseUrl = alternateHost
            retryCall
        } else {
            // Both tiers failed - reset to primary so the next request starts the cycle fresh.
            BackendConfig.activeBaseUrl = BackendConfig.PRIMARY_URL
            retryCall ?: throw (retryException ?: firstException!!)
        }
    }
}
