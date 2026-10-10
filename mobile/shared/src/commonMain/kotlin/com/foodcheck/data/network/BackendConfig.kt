package com.foodcheck.data.network

/**
 * Backend host for the app. Neon Functions only — no secondary / k3 fallback.
 *
 * [customBaseUrl], when set (e.g. LAN for local dev), overrides [NEON_URL].
 */
object BackendConfig {
    const val NEON_URL =
        "https://br-hidden-waterfall-b5gh3ebj-foodcheck.compute.c-7.us-east-2.aws.neon.tech"

    /** Set to a LAN URL for local dev; null uses Neon. */
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

    val baseUrl: String
        get() = customBaseUrl ?: NEON_URL
}
