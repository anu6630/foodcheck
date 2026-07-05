package com.foodcheck.data.network

/**
 * Single source of truth for which backend host the app talks to.
 *
 * [activeBaseUrl] is the tier currently believed to be healthy (updated both reactively,
 * by [installFallbackInterceptor] on request failure, and proactively, by [ApiHealthMonitor]'s
 * periodic /health poll). [customBaseUrl], when set, overrides both and disables fallback.
 */
object BackendConfig {
    const val PRIMARY_URL = "https://foodcheck-aws-api.lifex.biz"
    const val SECONDARY_URL = "https://foodcheck-k3-api.anuraj.net"

    var activeBaseUrl: String = PRIMARY_URL
        internal set

    var customBaseUrl: String? = "http://192.168.1.5:8999"

    /** Effective base URL requests should use: manual override wins, else the active tier. */
    val baseUrl: String
        get() = customBaseUrl ?: activeBaseUrl

    fun alternateOf(url: String): String = if (url == PRIMARY_URL) SECONDARY_URL else PRIMARY_URL
}
