package com.foodcheck.app.update

/**
 * Points at the shared Lifex App Config Neon Function (`appcfg`).
 * Policy is driven remotely — bump latest / min_supported in that service.
 */
object AppUpdateGate {
    const val BASE_URL =
        "https://br-hidden-waterfall-b5gh3ebj-appcfg.compute.c-7.us-east-2.aws.neon.tech"
    const val APP_KEY = "foodcheck"
}
