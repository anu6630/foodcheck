package com.foodcheck

import com.foodcheck.data.dto.ScanQuotaExceededDto
import com.foodcheck.data.dto.ScanResponse
import com.foodcheck.domain.entities.ScanQuotaExceededException
import com.foodcheck.util.sha256Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

class ScanQuotaDecodeTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun decodesQuotaInScanResponse() {
        val raw = """
        {
          "match_type": "ingredients_only",
          "ingredients": [],
          "detected": { "product_name": "Test Milk" },
          "quota": {
            "used": 2,
            "limit": 5,
            "topup_left": 1,
            "resets_at": "2026-11-01T00:00:00.000Z"
          }
        }
        """.trimIndent()

        val resp = json.decodeFromString(ScanResponse.serializer(), raw)
        assertNotNull(resp.quota)
        assertEquals(2, resp.quota?.used)
        assertEquals(5, resp.quota?.limit)
        assertEquals(1, resp.quota?.topupLeft)
        assertEquals("2026-11-01T00:00:00.000Z", resp.quota?.resetsAt)

        val domainQuota = resp.quota!!.toDomain()
        assertEquals(4, domainQuota.remaining)
        assertFalse(domainQuota.isExceeded)
    }

    @Test
    fun decodesScanQuotaExceededResponse() {
        val raw402 = """
        {
          "code": "SCAN_QUOTA_EXCEEDED",
          "detail": "Monthly AI scan limit reached",
          "quota": {
            "used": 5,
            "limit": 5,
            "topup_left": 0,
            "resets_at": "2026-11-01T00:00:00.000Z"
          }
        }
        """.trimIndent()

        val dto = json.decodeFromString(ScanQuotaExceededDto.serializer(), raw402)
        assertEquals("SCAN_QUOTA_EXCEEDED", dto.code)
        assertNotNull(dto.quota)

        val quota = dto.quota!!.toDomain()
        assertTrue(quota.isExceeded)
        assertEquals(0, quota.remaining)

        val ex = ScanQuotaExceededException(quota)
        assertTrue(ex.message?.contains("Monthly AI scan limit reached") == true)
    }

    @Test
    fun sha256HexMatchesKnownDigest() {
        // Known SHA-256 for "hello"
        val input = "hello".encodeToByteArray()
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(expected, sha256Hex(input))
    }
}
