package com.foodcheck.data.datasources

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
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

    companion object {
        private const val PRIMARY_URL = "https://foodcheck-aws-api.lifex.biz"
        private const val SECONDARY_URL = "https://foodcheck-k3-api.anuraj.net"
    }

    private var activeBaseUrl = PRIMARY_URL
    private var customBaseUrl: String? = null

    var baseUrl: String
        get() = customBaseUrl ?: activeBaseUrl
        set(value) {
            customBaseUrl = value
        }

    private suspend fun <T> runWithFallback(block: suspend (String) -> T): T {
        val custom = customBaseUrl
        if (custom != null) {
            return block(custom)
        }
        try {
            return block(activeBaseUrl)
        } catch (e: Exception) {
            println("Request failed on $activeBaseUrl: ${e.message}. Trying fallback...")
            if (activeBaseUrl == PRIMARY_URL) {
                activeBaseUrl = SECONDARY_URL
                try {
                    return block(activeBaseUrl)
                } catch (e2: Exception) {
                    activeBaseUrl = PRIMARY_URL // Reset to primary
                    throw e2
                }
            } else {
                activeBaseUrl = PRIMARY_URL // Reset to primary
                throw e
            }
        }
    }

    suspend fun searchProducts(query: String, countries: List<String>): List<SearchProductResult> = runWithFallback { baseUrl ->
        val response = client.get("$baseUrl/api/products/search") {
            parameter("q", query)
            parameter("countries", countries.joinToString(","))
        }
        val text = response.bodyAsText()
        json.decodeFromString(ListSerializer(SearchProductResult.serializer()), text)
    }

    suspend fun getProductDetails(barcode: String, countries: List<String>): SearchProductResult? = runWithFallback { baseUrl ->
        val response = client.get("$baseUrl/api/products/$barcode") {
            parameter("countries", countries.joinToString(","))
        }
        if (response.status.value == 404) return@runWithFallback null
        val text = response.bodyAsText()
        json.decodeFromString(SearchProductResult.serializer(), text)
    }

    suspend fun scanImage(imageBytes: ByteArray, countries: List<String>): ScanResult = runWithFallback { baseUrl ->
        val response = client.submitFormWithBinaryData(
            url = "$baseUrl/api/scan",
            formData = formData {
                append("file", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"scan.jpg\"")
                })
                append("countries", countries.joinToString(","))
            }
        )
        val text = response.bodyAsText()
        json.decodeFromString(ScanResult.serializer(), text)
    }
}
