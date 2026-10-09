package com.foodcheck.data.network

/**
 * Single source of truth for which backend host the app talks to.
 *
 * [activeBaseUrl] is the tier currently believed to be healthy (updated both reactively,
 * by [installFallbackInterceptor] on request failure, and proactively, by [ApiHealthMonitor]'s
 * periodic /health poll). [customBaseUrl], when set, overrides both and disables fallback.
 */
object BackendConfig {
    const val PRIMARY_URL =
        "https://br-hidden-waterfall-b5gh3ebj-foodcheck.compute.c-7.us-east-2.aws.neon.tech"
    const val SECONDARY_URL = "https://foodcheck-k3-api.anuraj.net"

    var activeBaseUrl: String = PRIMARY_URL
        internal set

    /** Set to a LAN URL for local dev; null uses [activeBaseUrl] (Neon by default). */
    var customBaseUrl: String? = null

    /** Anonymous device identity for shelf/stories. Persist on the platform; do not log. */
    var deviceKey: String = ""

    fun ensureDeviceKey(): String {
        if (deviceKey.isBlank()) {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
            deviceKey = buildString { repeat(32) { append(alphabet.random()) } }
        }
        return deviceKey
    }

    /** Effective base URL requests should use: manual override wins, else the active tier. */
    val baseUrl: String
        get() = customBaseUrl ?: activeBaseUrl

    fun alternateOf(url: String): String = if (url == PRIMARY_URL) SECONDARY_URL else PRIMARY_URL
}
