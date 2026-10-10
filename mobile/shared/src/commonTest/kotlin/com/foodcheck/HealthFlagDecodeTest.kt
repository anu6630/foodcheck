package com.foodcheck

import com.foodcheck.domain.entities.AnnotatedIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

class HealthFlagDecodeTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun decodesHealthWatchFields() {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        }
        val raw = """
        {"id":"aspartame","name":"Aspartame","category":null,"e_number":"E951","codes":[],
         "severity":"green","matched_bans":[],"market_code":"IN",
         "has_health_watch":true,
         "health_flags":[{"kind":"iarc_2b","label":"IARC 2B","value":"2B"}],
         "kind":"item","members":[]}
        """.trimIndent()
        val a = json.decodeFromString(AnnotatedIngredient.serializer(), raw)
        assertTrue(a.hasHealthWatch)
        assertTrue(a.showsWatch)
        assertEquals("IARC 2B", a.healthFlags.first().label)
    }
}
