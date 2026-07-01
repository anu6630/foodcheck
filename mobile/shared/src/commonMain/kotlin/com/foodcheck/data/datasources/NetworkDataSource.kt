package com.foodcheck.data.datasources

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import com.foodcheck.data.newClientRequestId
import com.foodcheck.data.network.BackendConfig
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.ScanResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

class NetworkDataSource(
    private val client: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Retained for the manual backend-URL override UI (App.kt); delegates to BackendConfig, the
    // single source of truth also used by the fallback interceptor and health monitor.
    var baseUrl: String
        get() = BackendConfig.baseUrl
        set(value) {
            BackendConfig.customBaseUrl = value
        }

    suspend fun searchProducts(query: String, countries: List<String>): List<SearchProductResult> {
        val response = client.get("${BackendConfig.baseUrl}/api/products/search") {
            parameter("q", query)
            parameter("countries", countries.joinToString(","))
        }
        val text = response.bodyAsText()
        return json.decodeFromString(ListSerializer(SearchProductResult.serializer()), text)
    }

    suspend fun getProductDetails(barcode: String, countries: List<String>): SearchProductResult? {
        val response = client.get("${BackendConfig.baseUrl}/api/products/$barcode") {
            parameter("countries", countries.joinToString(","))
        }
        if (response.status.value == 404) return null
        val text = response.bodyAsText()
        return json.decodeFromString(SearchProductResult.serializer(), text)
    }

    suspend fun scanImage(imageBytes: ByteArray, countries: List<String>): ScanResult {
        // Generated once per logical call; the fallback interceptor reuses this same request
        // (headers included) when it retries on the alternate host, so the key stays stable
        // across the retry.
        val idempotencyKey = newClientRequestId()
        val response = client.submitFormWithBinaryData(
            url = "${BackendConfig.baseUrl}/api/scan",
            formData = formData {
                append("file", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"scan.jpg\"")
                })
                append("countries", countries.joinToString(","))
            }
        ) {
            header("Idempotency-Key", idempotencyKey)
        }
        val text = response.bodyAsText()
        return json.decodeFromString(ScanResult.serializer(), text)
    }
}
