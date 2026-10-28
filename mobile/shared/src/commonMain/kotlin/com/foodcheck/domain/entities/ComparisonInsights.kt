package com.foodcheck.domain.entities

/**
 * Plain-language differences between the compared labels, read straight off a [ComparisonMatrix]
 * so users get the gist before the full table. Facts only — no ranking or "best" product.
 * Product indices follow [ComparisonMatrix.products].
 */
sealed interface ComparisonInsight {
    /** A flagged substance and the products whose label carries it. */
    data class Flagged(val row: ComparisonRow) : ComparisonInsight

    /** A main ingredient (top of some label) that other products don't have at all. */
    data class MainOnlyIn(val row: ComparisonRow) : ComparisonInsight

    /** A main ingredient in every product that sits highest (= most of it) on [product]'s label. */
    data class HighestIn(val row: ComparisonRow, val product: Int) : ComparisonInsight

    /** [count] ingredients appear on [product]'s label and no other. */
    data class OnlyOnOne(val product: Int, val count: Int) : ComparisonInsight

    /** [count] ingredients appear on every label. */
    data class Shared(val count: Int) : ComparisonInsight

    /** Every label lists the same ingredients in the same order. */
    data object SameLabel : ComparisonInsight
}

private const val MAX_FLAGGED_INSIGHTS = 3
private const val MAX_MAIN_INSIGHTS = 2
private const val MAX_INSIGHTS = 6

/**
 * Most decision-relevant first: flagged substances, then main-ingredient differences, then counts.
 * Child-warning colours are left out because [ComparisonMatrix.childWarnings] has its own callout.
 */
fun buildComparisonInsights(matrix: ComparisonMatrix): List<ComparisonInsight> {
    val rows = matrix.flagged + matrix.main + matrix.onlyInSome + matrix.inAll
    if (rows.all { row -> row.positions.all { it == row.positions.first() && it != null } }) {
        return listOf(ComparisonInsight.SameLabel)
    }
    val childKeys = matrix.childWarnings.mapTo(HashSet()) { it.key }

    val flagged = matrix.flagged
        .filter { it.key !in childKeys }
        .take(MAX_FLAGGED_INSIGHTS)
        .map { ComparisonInsight.Flagged(it) }

    val mainOnly = matrix.main
        .filter { row -> row.positions.any { it == null } }
        .take(MAX_MAIN_INSIGHTS)
        .map { ComparisonInsight.MainOnlyIn(it) }

    // Biggest gap first: "2nd vs 6th" says more than "1st vs 2nd".
    val highest = matrix.main
        .filter { row -> row.positions.all { it != null } }
        .mapNotNull { row ->
            val pos = row.positions.map { it!! }
            val min = pos.min()
            if (pos.count { it == min } != 1) null else row to (pos.max() - min)
        }
        .sortedByDescending { it.second }
        .take(MAX_MAIN_INSIGHTS)
        .map { (row, _) -> ComparisonInsight.HighestIn(row, row.positions.indexOf(row.positions.filterNotNull().min())) }

    val onlyOnOne = matrix.products.indices.mapNotNull { product ->
        val count = rows.count { row ->
            row.positions[product] != null && row.positions.count { it != null } == 1
        }
        if (count > 0) ComparisonInsight.OnlyOnOne(product, count) else null
    }

    val shared = rows.count { row -> row.positions.all { it != null } }
        .takeIf { it > 0 }
        ?.let { ComparisonInsight.Shared(it) }

    return (flagged + mainOnly + highest + onlyOnOne + listOfNotNull(shared)).take(MAX_INSIGHTS)
}
