package com.foodcheck

import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.HealthFlag
import com.foodcheck.domain.entities.buildComparisonMatrix
import kotlin.test.Test
import kotlin.test.assertEquals

class ComparisonMatrixTest {
    private fun ing(
        name: String,
        id: String? = name.lowercase(),
        severity: String = "green",
        watch: Boolean = false,
    ) = AnnotatedIngredient(
        id = id,
        name = name,
        severity = severity,
        hasHealthWatch = watch,
        healthFlags = if (watch) listOf(HealthFlag(kind = "label_warning_hyperactivity", label = "Hyperactivity")) else emptyList(),
    )

    private val flour = ing("Wheat flour")
    private val sugar = ing("Sugar")
    private val palm = ing("Palm oil")
    private val salt = ing("Salt")
    private val milk = ing("Milk solids")
    private val raising = ing("Raising agent 500(ii)")
    private val tartrazine = ing("Tartrazine", severity = "amber", watch = true)

    @Test
    fun splitsRowsIntoExclusiveSectionsWithLabelPositions() {
        val a = listOf(flour, sugar, palm, salt, raising, tartrazine)
        val b = listOf(sugar, flour, milk, salt, raising)
        val m = buildComparisonMatrix(listOf(a, b))

        assertEquals(listOf("tartrazine"), m.flagged.map { it.key })
        assertEquals(listOf(6, null), m.flagged.single().positions)
        assertEquals(listOf("wheat flour", "sugar", "palm oil", "milk solids"), m.main.map { it.key })
        assertEquals(listOf(1, 2), m.main.first().positions)
        assertEquals(listOf("salt", "raising agent 500(ii)"), m.inAll.map { it.key })
        assertEquals(emptyList(), m.onlyInSome.map { it.key })
    }

    @Test
    fun summariesCountFlaggedAndRegulationSignals() {
        val m = buildComparisonMatrix(
            listOf(
                listOf(flour, tartrazine, ing("Lead chromate", severity = "red")),
                listOf(flour, sugar),
            ),
        )
        val a = m.products[0]
        assertEquals(3, a.total)
        assertEquals(1, a.banned)
        assertEquals(1, a.restricted)
        assertEquals(1, a.watch)
        assertEquals(2, a.flagged)
        assertEquals(0, m.products[1].flagged)
    }

    @Test
    fun matchesUnmatchedNamesIgnoringCaseAndPunctuation() {
        val m = buildComparisonMatrix(
            listOf(
                listOf(flour, sugar, palm, ing("Iodised Salt", id = null)),
                listOf(flour, sugar, palm, ing("iodised salt.", id = null)),
                listOf(flour, sugar, palm, milk, ing("Cocoa", id = null)),
            ),
        )
        val salt = m.onlyInSome.first { it.ingredient.name.startsWith("Iodised", true) }
        assertEquals(listOf(4, 4, null), salt.positions)
        assertEquals(listOf("name:iodisedsalt", "milk solids", "name:cocoa"), m.onlyInSome.map { it.key })
    }

    @Test
    fun expandsGroupsIntoMembersForPositions() {
        val colours = AnnotatedIngredient(
            name = "Colours",
            severity = "gray",
            kind = "group",
            members = listOf(tartrazine, ing("Caramel")),
        )
        val m = buildComparisonMatrix(listOf(listOf(flour, colours), listOf(flour)))
        assertEquals(listOf(2, null), m.flagged.single().positions)
        assertEquals(listOf(3, null), m.main.single { it.key == "caramel" }.positions)
    }

    @Test
    fun usesLabelOrderNotDisplayOrderAndGroupsShareTheirPosition() {
        // Scan display order is flagged-first; `order` carries the real label position.
        val colours = AnnotatedIngredient(
            name = "Colours",
            severity = "amber",
            kind = "group",
            order = 10,
            members = listOf(tartrazine, ing("Sunset yellow", severity = "amber")),
        )
        val scanned = listOf(colours, flour.copy(order = 1), sugar.copy(order = 2), salt.copy(order = 8))
        val saved = listOf(flour.copy(order = 1), sugar.copy(order = 2), salt.copy(order = 7))
        val m = buildComparisonMatrix(listOf(scanned, saved))

        assertEquals(listOf(10, null), m.flagged.first { it.key == "tartrazine" }.positions)
        assertEquals(listOf(10, null), m.flagged.first { it.key == "sunset yellow" }.positions)
        assertEquals(listOf(listOf(1, 1), listOf(2, 2)), m.main.map { it.positions })
        assertEquals(listOf(8, 7), m.inAll.single().positions)
    }

    @Test
    fun callsOutOnlyColoursWithTheChildrenWarning() {
        val bha = ing("BHA", severity = "amber").copy(
            hasHealthWatch = true,
            healthFlags = listOf(HealthFlag(kind = "iarc", label = "IARC 2B")),
        )
        val m = buildComparisonMatrix(listOf(listOf(flour, tartrazine, bha), listOf(flour, bha)))
        assertEquals(listOf("bha", "tartrazine"), m.flagged.map { it.key }.sorted())
        assertEquals(listOf("tartrazine"), m.childWarnings.map { it.key })
        assertEquals(listOf(2, null), m.childWarnings.single().positions)
    }
}
