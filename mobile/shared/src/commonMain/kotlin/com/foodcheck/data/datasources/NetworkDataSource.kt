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
import io.ktor.http.isSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.HttpResponse
import com.foodcheck.data.newClientRequestId
import com.foodcheck.data.dto.ScanResponse
import com.foodcheck.data.dto.RequestSubmitResponse
import com.foodcheck.data.dto.BrandListResponse
import com.foodcheck.data.dto.CommunityIdResponse
import com.foodcheck.data.dto.CommentsResponse
import com.foodcheck.data.dto.CompanyListResponse
import com.foodcheck.data.dto.LikeResponse
import com.foodcheck.data.dto.ShelfListResponse
import com.foodcheck.data.dto.StoryDto
import com.foodcheck.data.dto.StoryListResponse
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
    // Base URL: Neon Functions (see BackendConfig).
    var baseUrl: String
        get() = BackendConfig.baseUrl
        set(value) {
            BackendConfig.customBaseUrl = value
        }

    private fun io.ktor.client.request.HttpRequestBuilder.deviceKey() {
        header("X-Device-Key", BackendConfig.ensureDeviceKey())
    }

    suspend fun saveToShelf(
        displayName: String,
        brand: String?,
        brandId: Int? = null,
        companyIds: List<Int> = emptyList(),
        ingredientsText: String,
        snapshotJson: String,
        image: ByteArray?,
    ): CommunityIdResponse {
        val response = client.submitFormWithBinaryData(
            url = "${BackendConfig.baseUrl}/api/me/shelf",
            formData = formData {
                append("display_name", displayName)
                brand?.let { append("brand", it) }
                brandId?.let { append("brand_id", it.toString()) }
                if (companyIds.isNotEmpty()) {
                    append("company_ids", companyIds.joinToString(prefix = "[", postfix = "]"))
                }
                append("ingredients_text", ingredientsText)
                append("snapshot", snapshotJson)
                image?.let { bytes ->
                    append("image", bytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"label.jpg\"")
                    })
                }
            }
        ) { deviceKey() }
        return decodeOrThrow(response.bodyAsText(), response.status.value, CommunityIdResponse.serializer())
    }

    suspend fun listShelf(query: String = ""): ShelfListResponse {
        val response = client.get("${BackendConfig.baseUrl}/api/me/shelf") {
            deviceKey()
            if (query.isNotBlank()) parameter("q", query)
        }
        return decodeOrThrow(response.bodyAsText(), response.status.value, ShelfListResponse.serializer())
    }

    suspend fun listBrands(query: String = "", limit: Int = 20): BrandListResponse {
        val response = client.get("${BackendConfig.baseUrl}/api/brands") {
            if (query.isNotBlank()) parameter("q", query)
            parameter("limit", limit.toString())
        }
        return decodeOrThrow(response.bodyAsText(), response.status.value, BrandListResponse.serializer())
    }

    suspend fun listCompanies(query: String = "", limit: Int = 20): CompanyListResponse {
        val response = client.get("${BackendConfig.baseUrl}/api/companies") {
            if (query.isNotBlank()) parameter("q", query)
            parameter("limit", limit.toString())
        }
        return decodeOrThrow(response.bodyAsText(), response.status.value, CompanyListResponse.serializer())
    }

    suspend fun publishStory(
        caption: String,
        ingredientsText: String,
        snapshotJson: String,
        image: ByteArray?,
    ): CommunityIdResponse {
        val response = client.submitFormWithBinaryData(
            url = "${BackendConfig.baseUrl}/api/stories",
            formData = formData {
                if (caption.isNotBlank()) append("caption", caption)
                append("ingredients_text", ingredientsText)
                append("snapshot", snapshotJson)
                image?.let { bytes ->
                    append("image", bytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"label.jpg\"")
                    })
                }
            }
        ) { deviceKey() }
        return decodeOrThrow(response.bodyAsText(), response.status.value, CommunityIdResponse.serializer())
    }

    suspend fun listStories(limit: Int = 10, offset: Int = 0): StoryListResponse {
        val response = client.get("${BackendConfig.baseUrl}/api/stories") {
            parameter("limit", limit)
            parameter("offset", offset)
        }
        return decodeOrThrow(response.bodyAsText(), response.status.value, StoryListResponse.serializer())
    }

    suspend fun getStory(id: Int): StoryDto {
        val response = client.get("${BackendConfig.baseUrl}/api/stories/$id")
        return decodeOrThrow(response.bodyAsText(), response.status.value, StoryDto.serializer())
    }

    fun storyPhotoUrl(id: Int): String = "${BackendConfig.baseUrl}/api/stories/$id/photo"

    suspend fun addComment(storyId: Int, body: String): CommunityIdResponse {
        val response = client.post("${BackendConfig.baseUrl}/api/stories/$storyId/comments") {
            deviceKey()
            contentType(ContentType.Application.Json)
            setBody("""{"body":${json.encodeToString(kotlinx.serialization.serializer<String>(), body)}}""")
        }
        return decodeOrThrow(response.bodyAsText(), response.status.value, CommunityIdResponse.serializer())
    }

    suspend fun toggleLike(storyId: Int): LikeResponse {
        val response = client.post("${BackendConfig.baseUrl}/api/stories/$storyId/like") { deviceKey() }
        return decodeOrThrow(response.bodyAsText(), response.status.value, LikeResponse.serializer())
    }

    suspend fun listComments(storyId: Int): CommentsResponse {
        val response = client.get("${BackendConfig.baseUrl}/api/stories/$storyId/comments")
        return json.decodeFromString(CommentsResponse.serializer(), response.bodyAsText())
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
        if (images.isEmpty()) throw Exception("No images to scan")
        val totalBytes = images.sumOf { it.size }
        if (totalBytes <= 0) throw Exception("Image data is empty")
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
        val text = requireSuccessBody(response, "Scan")
        return try {
            json.decodeFromString(ScanResponse.serializer(), text)
        } catch (e: Exception) {
            throw Exception("Scan response parse error: ${e.message}", e)
        }
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
        val text = requireSuccessBody(response, "Product request")
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

    private suspend fun requireSuccessBody(response: HttpResponse, action: String): String {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val detail = try {
                json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), text)["detail"]
                    ?.toString()?.trim('"')
            } catch (_: Exception) {
                text.take(240).ifBlank { null }
            }
            throw Exception(
                detail?.ifBlank { null }
                    ?: "$action failed (HTTP ${response.status.value})"
            )
        }
        return text
    }

    private fun <T> decodeOrThrow(
        text: String,
        status: Int,
        serializer: kotlinx.serialization.DeserializationStrategy<T>
    ): T {
        if (status >= 400) {
            val detail = try {
                json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), text)["detail"]
                    ?.toString()?.trim('"')
            } catch (_: Exception) {
                text
            }
            throw Exception(detail?.ifBlank { "Request failed ($status)" } ?: "Request failed ($status)")
        }
        return json.decodeFromString(serializer, text)
    }
}
