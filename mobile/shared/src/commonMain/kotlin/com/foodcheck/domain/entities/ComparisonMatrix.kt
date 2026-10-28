package com.foodcheck.domain.entities

/**
 * Side-by-side view of 2–3 labels. Rows are distinct substances; each cell is the 1-based
 * position of that substance's entry on the product's label (null = not on that label).
 *
 * Sections are mutually exclusive and shown in this order:
 * [flagged] → [main] (in the first [MAIN_POSITION_LIMIT] of any label) → [onlyInSome] → [inAll].
 * [childWarnings] is a subset of [flagged], called out above the sections.
 */
data class ComparisonMatrix(
    val products: List<ComparedProductSummary>,
    val flagged: List<ComparisonRow>,
    val main: List<ComparisonRow>,
    val onlyInSome: List<ComparisonRow>,
    val inAll: List<ComparisonRow>,
    /** Flagged rows whose label must carry the EU children's activity/attention warning. */
    val childWarnings: List<ComparisonRow> = emptyList(),
) {
    companion object {
        const val MAIN_POSITION_LIMIT = 3

        /** Health-flag kind for the six "Southampton" colours (EU Reg. 1333/2008, Annex V). */
        const val CHILD_HYPERACTIVITY_KIND = "label_warning_hyperactivity"
    }
}

data class ComparedProductSummary(
    val total: Int,
    val banned: Int,
    val restricted: Int,
    val watch: Int,
    val flagged: Int,
    /** True when the label has a colour that needs the EU children's warning. */
    val childWarning: Boolean = false,
)

data class ComparisonRow(
    val key: String,
    /** Best-annotated instance (for name, codes, severity, health flags, ingredient id). */
    val ingredient: AnnotatedIngredient,
    val positions: List<Int?>,
) {
    val isFlagged: Boolean get() = ingredient.isComparisonFlagged()
}

/** Same rule as the backend's `isFlagged`: regulation signal or health watch. */
fun AnnotatedIngredient.isComparisonFlagged(): Boolean {
    val s = severity.lowercase()
    return s == "red" || s == "amber" || hasHealthWatch || healthFlags.isNotEmpty()
}

/**
 * Groups expanded to substances; members inherit their group's label position ([AnnotatedIngredient.order]),
 * because "Colours (102, 110)" is one entry on the pack. Keeps the incoming order.
 */
fun List<AnnotatedIngredient>.expandWithLabelOrder(): List<AnnotatedIngredient> =
    flatMap { ing ->
        ing.forSnapshot().map { m -> if (m.order <= 0 && ing.order > 0) m.copy(order = ing.order) else m }
    }

/** [expandWithLabelOrder], sorted into label order (items without a position keep their place at the end). */
fun List<AnnotatedIngredient>.flattenForComparison(): List<AnnotatedIngredient> =
    expandWithLabelOrder().sortedBy { if (it.order > 0) it.order else Int.MAX_VALUE }

/** Label position: the scanned/saved one when known, else list position (older saves). */
private fun labelPosition(ing: AnnotatedIngredient, index: Int): Int = if (ing.order > 0) ing.order else index + 1

fun comparisonKey(ingredient: AnnotatedIngredient): String =
    ingredient.id?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        ?: "name:" + ingredient.name.lowercase().filter { it.isLetterOrDigit() }

/** Earliest label position first; ties follow product A's order, then B's, then name. */
private val rowOrder = Comparator<ComparisonRow> { a, b ->
    val byMin = a.positions.filterNotNull().min().compareTo(b.positions.filterNotNull().min())
    if (byMin != 0) return@Comparator byMin
    for (i in a.positions.indices) {
        val c = (a.positions[i] ?: Int.MAX_VALUE).compareTo(b.positions[i] ?: Int.MAX_VALUE)
        if (c != 0) return@Comparator c
    }
    a.ingredient.name.lowercase().compareTo(b.ingredient.name.lowercase())
}

fun buildComparisonMatrix(labels: List<List<AnnotatedIngredient>>): ComparisonMatrix {
    val flat = labels.map { it.flattenForComparison() }
    val positions = LinkedHashMap<String, Array<Int?>>()
    val representative = HashMap<String, AnnotatedIngredient>()

    flat.forEachIndexed { productIndex, items ->
        items.forEachIndexed { index, ing ->
            val key = comparisonKey(ing)
            val cells = positions.getOrPut(key) { arrayOfNulls(flat.size) }
            if (cells[productIndex] == null) cells[productIndex] = labelPosition(ing, index)
            val current = representative[key]
            if (current == null || (current.id == null && ing.id != null)) representative[key] = ing
        }
    }

    val rows = positions.map { (key, cells) ->
        ComparisonRow(key = key, ingredient = representative.getValue(key), positions = cells.toList())
    }.sortedWith(rowOrder)

    val flagged = rows.filter { it.isFlagged }
    val rest = rows.filterNot { it.isFlagged }
    val main = rest.filter { row ->
        row.positions.any { it != null && it <= ComparisonMatrix.MAIN_POSITION_LIMIT }
    }
    val afterMain = rest - main.toSet()
    val inAll = afterMain.filter { row -> row.positions.all { it != null } }
    val onlyInSome = afterMain - inAll.toSet()

    val childWarnings = flagged.filter { row ->
        row.ingredient.healthFlags.any { it.kind == ComparisonMatrix.CHILD_HYPERACTIVITY_KIND }
    }
    val summaries = flat.mapIndexed { productIndex, items ->
        val distinct = items.distinctBy(::comparisonKey)
        ComparedProductSummary(
            total = distinct.size,
            banned = distinct.count { it.severity.equals("red", true) },
            restricted = distinct.count { it.severity.equals("amber", true) },
            watch = distinct.count { it.hasHealthWatch || it.healthFlags.isNotEmpty() },
            flagged = distinct.count { it.isComparisonFlagged() },
            childWarning = childWarnings.any { it.positions[productIndex] != null },
        )
    }

    return ComparisonMatrix(
        products = summaries,
        flagged = flagged,
        main = main,
        onlyInSome = onlyInSome,
        inAll = inAll,
        childWarnings = childWarnings,
    )
}
