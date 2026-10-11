package com.foodcheck

import com.foodcheck.data.dto.ScanResponse
import com.foodcheck.domain.entities.AssignedPhotos
import com.foodcheck.domain.entities.DetectedLabel
import com.foodcheck.domain.entities.assignPhotos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

class DetectedLabelTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun decodesDetectedBlockAndToleratesItsAbsence() {
        val withLabel = json.decodeFromString(
            ScanResponse.serializer(),
            """{"match_type":"ingredients_only","ingredients":[],
               "detected":{"product_name":"Cream Biscuits","brand":null,"category":"Biscuits",
               "image_roles":["front","ingredients"],"ingredients_complete":false}}""",
        )
        assertEquals(
            DetectedLabel("Cream Biscuits", null, "Biscuits", listOf("front", "ingredients"), false),
            withLabel.detected,
        )
        val legacy = json.decodeFromString(ScanResponse.serializer(), """{"match_type":"ingredients_only"}""")
        assertEquals(null, legacy.detected)
    }

    @Test
    fun assignsPhotosByRoleInAnyOrder() {
        assertEquals(AssignedPhotos("f", "i"), DetectedLabel(imageRoles = listOf("ingredients", "front")).assignPhotos(listOf("i", "f")))
        assertEquals(AssignedPhotos(null, "b"), DetectedLabel(imageRoles = listOf("both")).assignPhotos(listOf("b")))
        assertEquals(AssignedPhotos("f", null), DetectedLabel(imageRoles = listOf("front", "other")).assignPhotos(listOf("f", "x")))
    }

    @Test
    fun fallsBackToUploadOrderWithoutRoles() {
        assertEquals(AssignedPhotos(null, "a"), DetectedLabel().assignPhotos(listOf("a")))
        assertEquals(AssignedPhotos("a", "b"), DetectedLabel().assignPhotos(listOf("a", "b")))
    }

    @Test
    fun frontOnlyNeedsKnownRoles() {
        assertTrue(DetectedLabel(imageRoles = listOf("front")).frontOnly)
        assertFalse(DetectedLabel(imageRoles = listOf("front", "both")).frontOnly)
        assertFalse(DetectedLabel().frontOnly)
    }
}
