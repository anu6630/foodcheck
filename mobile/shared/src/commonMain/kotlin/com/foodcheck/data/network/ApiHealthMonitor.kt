package com.foodcheck.data.network

/**
 * Previously polled primary + secondary and steered [BackendConfig.activeBaseUrl].
 * Secondary is retired; Neon is the only host. Kept so Android/iOS Koin startup stays unchanged.
 */
class ApiHealthMonitor {
    fun start() {
        // No-op: no tier switching.
    }

    fun stop() {
        // No-op.
    }
}
