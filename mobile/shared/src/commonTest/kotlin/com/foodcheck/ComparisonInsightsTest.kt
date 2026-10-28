package com.foodcheck

import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.ComparisonInsight
import com.foodcheck.domain.entities.HealthFlag
import com.foodcheck.domain.entities.buildComparisonInsights
import com.foodcheck.domain.entities.buildComparisonMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComparisonInsightsTest {
    private fun ing(name: String, severity: String = "green", flag: String? = null) = AnnotatedIngredient(
        id = name.lowercase(),
        name = name,
        severity = severity,
        healthFlags = flag?.let { listOf(HealthFlag(kind = it, label = it)) } ?: emptyList(),
    )

    private val flour = ing("Wheat flour")
    private val sugar = ing("Sugar")
    private val palm = ing("Palm oil")
    private val salt = ing("Salt")
    private val milk = ing("Milk solids")
    private val cocoa = ing("Cocoa")
    private val bha = ing("BHA", severity = "amber", flag = "iarc")
    private val tartrazine = ing("Tartrazine", severity = "amber", flag = "label_warning_hyperactivity")

    @Test
    fun ordersFlaggedThenMainDifferencesThenCounts() {
        val a = listOf(flour, palm, sugar, salt, bha)
        val b = listOf(flour, sugar, milk, salt, cocoa)
        val insights = buildComparisonInsights(buildComparisonMatrix(listOf(a, b)))

        assertEquals(
            listOf(
                ComparisonInsight.Flagged::class,
                ComparisonInsight.MainOnlyIn::class,
                ComparisonInsight.MainOnlyIn::class,
                ComparisonInsight.HighestIn::class,
                ComparisonInsight.OnlyOnOne::class,
                ComparisonInsight.OnlyOnOne::class,
            ),
            insights.map { it::class },
        )
        assertEquals("bha", (insights[0] as ComparisonInsight.Flagged).row.key)
        assertEquals(listOf("palm oil", "milk solids"), insights.subList(1, 3).map { (it as ComparisonInsight.MainOnlyIn).row.key })
        val sugarHigh = insights[3] as ComparisonInsight.HighestIn
        assertEquals("sugar", sugarHigh.row.key)
        assertEquals(1, sugarHigh.product)
        // Palm oil + BHA only in A; milk + cocoa only in B. Shared (flour, sugar, salt) is cut by the cap.
        assertEquals(ComparisonInsight.OnlyOnOne(0, 2), insights[4])
        assertEquals(ComparisonInsight.OnlyOnOne(1, 2), insights[5])
    }

    @Test
    fun leavesChildWarningColoursToTheirCallout() {
        val m = buildComparisonMatrix(listOf(listOf(flour, sugar, tartrazine), listOf(flour, sugar)))
        val insights = buildComparisonInsights(m)
        assertTrue(insights.none { it is ComparisonInsight.Flagged })
        assertEquals(listOf(ComparisonInsight.OnlyOnOne(0, 1), ComparisonInsight.Shared(2)), insights)
        assertTrue(m.products[0].childWarning)
        assertTrue(!m.products[1].childWarning)
    }

    @Test
    fun skipsHighestWhenTiedAtTheTop() {
        val m = buildComparisonMatrix(listOf(listOf(flour, sugar, salt), listOf(flour, sugar, milk), listOf(flour, salt, sugar)))
        val insights = buildComparisonInsights(m)
        // Flour is 1st everywhere; sugar is 2nd in A and B (tie) — neither is a "highest in" fact.
        assertTrue(insights.none { it is ComparisonInsight.HighestIn })
        assertEquals(ComparisonInsight.Shared(2), insights.last())
    }

    @Test
    fun identicalLabelsCollapseToOneLine() {
        val m = buildComparisonMatrix(listOf(listOf(flour, sugar, salt), listOf(flour, sugar, salt)))
        assertEquals(listOf(ComparisonInsight.SameLabel), buildComparisonInsights(m))
    }
}
