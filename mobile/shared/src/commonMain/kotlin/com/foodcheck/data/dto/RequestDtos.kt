package com.foodcheck.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response returned after submitting either type of community request. */
@Serializable
data class RequestSubmitResponse(
    @SerialName("request_id") val requestId: Int,
    val status: String
)
