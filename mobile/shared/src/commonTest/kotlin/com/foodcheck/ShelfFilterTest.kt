package com.foodcheck

import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.data.repositories.filterShelfItems
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.IngredientCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShelfFilterTest {

    private val msgIngredient = AnnotatedIngredient(
        id = "monosodium-l-glutamate",
        name = "Monosodium L-glutamate",
        eNumber = "E621",
        codes = listOf(IngredientCode(system = "INS", code = "621", display = "INS 621")),
        severity = "green",
    )

    private val saltIngredient = AnnotatedIngredient(
        id = "salt",
        name = "Iodised Salt",
        severity = "green",
    )

    private val palmOil = AnnotatedIngredient(
        id = "palm-oil",
        name = "Edible Vegetable Oil (Palm)",
        severity = "amber",
    )

    private val maggiPack = ShelfItemDto(
        id = 101,
        displayName = "Maggi 2-Minute Noodles",
        brand = "Nestlé",
        barcode = "8901058862492",
        stance = "avoid",
        ingredients = listOf(
            palmOil,
            saltIngredient,
            msgIngredient,
        ),
    )

    private val oatsPack = ShelfItemDto(
        id = 102,
        displayName = "Rolled Oats",
        brand = "Quaker",
        barcode = "8901058862000",
        stance = "ok",
        ingredients = listOf(
            AnnotatedIngredient(
                id = "rolled-oats",
                name = "Whole Grain Rolled Oats",
                severity = "green",
            )
        ),
    )

    private val allPacks = listOf(maggiPack, oatsPack)

    @Test
    fun matchesDirectlyByDisplayNameOrBrand() {
        val result = filterShelfItems(allPacks, "maggi")
        assertEquals(1, result.size)
        assertEquals("Maggi 2-Minute Noodles", result.first().displayName)

        val brandResult = filterShelfItems(allPacks, "nestle")
        assertEquals(1, brandResult.size)
        assertEquals(101, brandResult.first().id)
    }

    @Test
    fun matchesDirectlyByIngredientName() {
        val result = filterShelfItems(allPacks, "oats")
        assertEquals(1, result.size)
        assertEquals(102, result.first().id)
    }

    @Test
    fun matchesByResolvedSynonymIngredientIdWhenQueryDoesNotAppearOnPackLabel() {
        // User typed "ajinomoto", but the physical pack says "Monosodium L-glutamate" (id: "monosodium-l-glutamate").
        // Without matchedIngredientIds, searching "ajinomoto" wouldn't match Maggi.
        val unmatchedWithoutSynonyms = filterShelfItems(allPacks, "ajinomoto")
        assertTrue(unmatchedWithoutSynonyms.isEmpty())

        // With backend synonym resolution providing matchedIngredientIds:
        val matchedWithSynonyms = filterShelfItems(
            items = allPacks,
            query = "ajinomoto",
            matchedIngredientIds = setOf("monosodium-l-glutamate", "E621", "621")
        )
        assertEquals(1, matchedWithSynonyms.size)
        assertEquals("Maggi 2-Minute Noodles", matchedWithSynonyms.first().displayName)
    }

    @Test
    fun matchesNestedGroupMembers() {
        val flavourGroup = AnnotatedIngredient(
            name = "Flavour Enhancers",
            severity = "green",
            kind = "group",
            members = listOf(msgIngredient),
        )
        val soupPack = ShelfItemDto(
            id = 103,
            displayName = "Hot & Sour Soup",
            brand = "Knorr",
            ingredients = listOf(flavourGroup),
        )

        val result = filterShelfItems(
            items = listOf(soupPack),
            query = "ajinomoto",
            matchedIngredientIds = setOf("monosodium-l-glutamate")
        )
        assertEquals(1, result.size)
        assertEquals(103, result.first().id)
    }

    @Test
    fun returnsEmptyWhenNoSavedPackMatches() {
        val result = filterShelfItems(
            items = allPacks,
            query = "tartrazine",
            matchedIngredientIds = setOf("tartrazine", "E102")
        )
        assertTrue(result.isEmpty())
    }
}
