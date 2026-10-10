package com.foodcheck.data.network

import io.ktor.client.HttpClient

/**
 * Previously retried failed requests on a secondary host (foodcheck-k3-api.anuraj.net).
 * That tier is retired — Neon is the only backend. Kept as a no-op so call sites stay stable.
 */
fun HttpClient.installFallbackInterceptor() {
    // No alternate host. Requests use [BackendConfig.baseUrl] only.
}
