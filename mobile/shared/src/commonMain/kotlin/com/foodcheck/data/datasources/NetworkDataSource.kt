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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.foodcheck.data.newClientRequestId
import com.foodcheck.data.dto.ScanResponse
import com.foodcheck.data.dto.RequestSubmitResponse
import com.foodcheck.data.network.BackendConfig
import com.foodcheck.domain.entities.IngredientDetail
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.UnifiedSearchResult
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.builtins.ListSerializer

class NetworkDataSource(
    private val client: HttpClient
) {
    // Backend serializes all fields as snake_case (e.g. "e_number", "matched_bans");
    // this bridges that to the Kotlin models' camelCase property names.
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    // Retained for the manual backend-URL override UI (App.kt); delegates to BackendConfig, the
    // single source of truth also used by the fallback interceptor and health monitor.
    var baseUrl: String
        get() = BackendConfig.baseUrl
        set(value) {
            BackendConfig.customBaseUrl = value
        }

    suspend fun searchProducts(query: String): List<SearchProductResult> {
        val response = client.get("${BackendConfig.baseUrl}/api/products/search") {
            parameter("q", query)
        }
        val text = response.bodyAsText()
        return json.decodeFromString(ListSerializer(SearchProductResult.serializer()), text)
    }

    suspend fun getProductDetails(barcode: String): SearchProductResult? {
        val response = client.get("${BackendConfig.baseUrl}/api/products/$barcode")
        if (response.status.value == 404) return null
        val text = response.bodyAsText()
        return json.decodeFromString(SearchProductResult.serializer(), text)
    }

    suspend fun unifiedSearch(query: String): UnifiedSearchResult {
        val response = client.get("${BackendConfig.baseUrl}/api/search") {
            parameter("q", query)
        }
        val text = response.bodyAsText()
        return json.decodeFromString(UnifiedSearchResult.serializer(), text)
    }

    suspend fun getIngredientDetails(ingredientId: String): IngredientDetail {
        val response = client.get("${BackendConfig.baseUrl}/api/ingredients/$ingredientId")
        val text = response.bodyAsText()
        return json.decodeFromString(IngredientDetail.serializer(), text)
    }

    suspend fun scan(images: List<ByteArray>): ScanResponse {
        // Generated once per logical call; the fallback interceptor reuses this same request
        // (headers included) when it retries on the alternate host, so the key stays stable
        // across the retry.
        val idempotencyKey = newClientRequestId()
        val response = client.submitFormWithBinaryData(
            url = "${BackendConfig.baseUrl}/api/scan",
            formData = formData {
                images.forEachIndexed { index, bytes ->
                    // FastAPI's `images: List[UploadFile]` binds every part sharing this same
                    // field name into the list, so each photo is appended under "images".
                    append("images", bytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"scan_$index.jpg\"")
                    })
                }
            }
        ) {
            header("Idempotency-Key", idempotencyKey)
        }
        val text = response.bodyAsText()
        return json.decodeFromString(ScanResponse.serializer(), text)
    }

    /** Flow 1: Submit a product-addition request with optional images (up to 3). */
    suspend fun submitProductRequest(
        productName: String,
        countryCode: String,
        brand: String? = null,
        barcode: String? = null,
        category: String? = null,
        ingredientsText: String? = null,
        requesterNotes: String? = null,
        images: List<ByteArray> = emptyList(),
    ): RequestSubmitResponse {
        val response = client.submitFormWithBinaryData(
            url = "${BackendConfig.baseUrl}/api/requests/product",
            formData = formData {
                append("product_name", productName)
                append("country_code", countryCode)
                brand?.let { append("brand", it) }
                barcode?.let { append("barcode", it) }
                category?.let { append("category", it) }
                ingredientsText?.let { append("ingredients_text", it) }
                requesterNotes?.let { append("requester_notes", it) }
                images.getOrNull(0)?.let { bytes ->
                    append("image_1", bytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"image_1.jpg\"")
                    })
                }
                images.getOrNull(1)?.let { bytes ->
                    append("image_2", bytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"image_2.jpg\"")
                    })
                }
                images.getOrNull(2)?.let { bytes ->
                    append("image_3", bytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"image_3.jpg\"")
                    })
                }
            }
        )
        val text = response.bodyAsText()
        return json.decodeFromString(RequestSubmitResponse.serializer(), text)
    }

    /** Flow 2: Submit an ingredient check request (JSON body). */
    suspend fun submitIngredientRequest(
        ingredientName: String,
        eNumber: String? = null,
        category: String? = null,
        requesterNotes: String? = null,
    ): RequestSubmitResponse {
        val response = client.post("${BackendConfig.baseUrl}/api/requests/ingredient") {
            contentType(ContentType.Application.Json)
            setBody(
                buildString {
                    append("{")
                    append("\"ingredient_name\":\"${ingredientName}\"")
                    eNumber?.let { append(",\"e_number\":\"$it\"") }
                    category?.let { append(",\"category\":\"$it\"") }
                    requesterNotes?.let { append(",\"requester_notes\":\"$it\"") }
                    append("}")
                }
            )
        }
        val text = response.bodyAsText()
        return json.decodeFromString(RequestSubmitResponse.serializer(), text)
    }
}
