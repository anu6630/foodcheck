package com.foodcheck.data

/** UUID v4 for client-generated request/idempotency identifiers. */
fun newClientRequestId(): String = buildString(36) {
    val hex = "0123456789abcdef"
    val rnd = kotlin.random.Random.Default
    for (i in 0 until 36) {
        append(
            when (i) {
                8, 13, 18, 23 -> '-'
                14 -> '4'
                19 -> hex[rnd.nextInt(4) + 8]
                else -> hex[rnd.nextInt(16)]
            },
        )
    }
}
