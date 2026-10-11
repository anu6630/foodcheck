package com.foodcheck.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.ComparedProductSummary
import com.foodcheck.domain.entities.ComparisonMatrix
import com.foodcheck.domain.entities.ComparisonRow
import com.foodcheck.presentation.state.CompareDecision
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.util.decodeImageBitmap

/** Fixed width of one product column so names, counts and positions line up. */
val CompareCellWidth: Dp = 54.dp

/** Loads one photo for a slot; returns null when there is none. */
typealias PhotoLoader = suspend (kind: String) -> ByteArray?

data class ComparisonColumn(
    val slot: Int,
    val letter: String,
    val name: String,
    val summary: ComparedProductSummary,
    val photoLoader: PhotoLoader,
    val chosen: Boolean = false,
)

val COMPARE_REASONS = listOf(
    "Fewer flagged", "No added colours", "Child likes it", "Price", "Brand I trust",
)

@Composable
fun SlotThumb(
    loader: PhotoLoader,
    letter: String,
    size: Dp,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, loader) {
        value = (loader("front") ?: loader("label"))?.let { decodeImageBitmap(it) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Primary.copy(alpha = 0.10f))
            .border(
                if (highlight) 2.dp else 1.dp,
                if (highlight) AppColors.Primary else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it, contentDescription = "Product $letter", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } ?: Text(letter, color = AppColors.Primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

/** Sticky header: one column per product with photo, name and flagged count. */
@Composable
fun ComparisonHeader(
    columns: List<ComparisonColumn>,
    onColumnClick: (ComparisonColumn) -> Unit,
    onPhotoClick: (ComparisonColumn) -> Unit,
    hint: String,
) {
    Surface(color = AppColors.BackgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text("Flagged", color = AppColors.TextMuted, fontSize = 11.sp)
                Text("Ingredients", color = AppColors.TextMuted.copy(alpha = 0.7f), fontSize = 11.sp)
                Text(hint, color = AppColors.TextMuted.copy(alpha = 0.45f), fontSize = 9.sp)
            }
            columns.forEach { col ->
                Column(
                    modifier = Modifier.width(CompareCellWidth + 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SlotThumb(
                        loader = col.photoLoader,
                        letter = col.letter,
                        size = 40.dp,
                        highlight = col.chosen,
                        onClick = { onPhotoClick(col) },
                    )
                    Text(
                        col.name,
                        modifier = Modifier.clickable { onColumnClick(col) }.padding(top = 2.dp),
                        color = if (col.chosen) AppColors.Primary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    val s = col.summary
                    Text(
                        if (s.flagged == 0) "0" else "${s.flagged} ⚑",
                        color = when {
                            s.banned > 0 -> AppColors.Red
                            s.flagged > 0 -> AppColors.Amber
                            else -> AppColors.Green
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("${s.total}", color = AppColors.TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

enum class MatrixSection(val title: String) {
    Flagged("Flagged"),
    Main("Main ingredients"),
    OnlyInSome("Only in some"),
    InAll("In all"),
}

/**
 * Adds the grouped rows to a LazyColumn. [expanded] holds the open sections; [letters] names the
 * product columns (A, B, C) in matrix order.
 */
fun LazyListScope.comparisonSections(
    matrix: ComparisonMatrix,
    letters: List<String>,
    expanded: Set<MatrixSection>,
    onToggle: (MatrixSection) -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
) {
    if (matrix.childWarnings.isNotEmpty()) {
        item(key = "child_warnings") { ChildWarningCallout(matrix.childWarnings, letters) }
    }
    val sections = listOf(
        MatrixSection.Flagged to matrix.flagged,
        MatrixSection.Main to matrix.main,
        MatrixSection.OnlyInSome to matrix.onlyInSome,
        MatrixSection.InAll to matrix.inAll,
    )
    sections.forEach { (section, rows) ->
        item(key = "h_${section.name}") {
            val open = section in expanded
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = rows.isNotEmpty()) { onToggle(section) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${section.title} ${rows.size}",
                    color = if (section == MatrixSection.Flagged && rows.isNotEmpty()) AppColors.Amber else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                if (section == MatrixSection.Flagged && rows.isEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("none in any product", color = AppColors.Green, fontSize = 12.sp)
                }
            }
        }
        if (section in expanded) {
            items(rows, key = { "r_${section.name}_${it.key}" }) { row ->
                ComparisonRowView(row, onIngredientClick)
            }
        }
    }
}

/** EU-mandated children's warning, shown to everyone because it is a fact about the colour. */
@Composable
private fun ChildWarningCallout(rows: List<ComparisonRow>, letters: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Amber.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            "⚠ May affect activity and attention in children",
            color = AppColors.Amber,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        rows.forEach { row ->
            val inProducts = row.positions.indices.filter { row.positions[it] != null }.joinToString(", ") { letters[it] }
            // Indian packs print the INS number ("Colour (129)"), so lead with it.
            val ins = row.ingredient.eNumber?.removePrefix("E")?.removePrefix("e")?.let { "INS $it · " }.orEmpty()
            Text("$ins${row.ingredient.name} — in $inProducts", color = Color.White, fontSize = 12.sp)
        }
        Text(
            "EU law requires this warning on packs with these colours (Reg. 1333/2008).",
            color = AppColors.TextMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ComparisonRowView(row: ComparisonRow, onIngredientClick: (AnnotatedIngredient) -> Unit) {
    val ing = row.ingredient
    val reason = if (row.isFlagged) {
        buildList {
            if (ing.severity.equals("red", true) || ing.severity.equals("amber", true)) add(ing.regulationLabel())
            ing.healthFlags.forEach { add(it.label) }
            if (isEmpty() && ing.hasHealthWatch) add("Health watch")
        }.distinct().joinToString(" · ")
    } else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = ing.id != null) { onIngredientClick(ing) }
            .padding(start = 36.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                ing.codesText()?.let { "${ing.name} · $it" } ?: ing.name,
                color = if (row.isFlagged) AppColors.severityColor(ing.severity).takeIf { ing.severity in setOf("red", "amber") } ?: AppColors.Amber else Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            reason?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = AppColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        row.positions.forEach { pos ->
            Text(
                pos?.let { "#$it" } ?: "·",
                modifier = Modifier.width(CompareCellWidth + 14.dp),
                color = if (pos == null) AppColors.TextMuted.copy(alpha = 0.4f) else Color.White,
                fontSize = 13.sp,
                fontWeight = if (pos != null && pos <= ComparisonMatrix.MAIN_POSITION_LIMIT) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Full-screen front + ingredients photos the user captured for one product. */
@Composable
fun ProductPhotosDialog(title: String, loader: PhotoLoader, onDismiss: () -> Unit) {
    val front by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, loader) {
        value = loader("front")?.let { decodeImageBitmap(it) }
    }
    val label by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, loader) {
        value = loader("label")?.let { decodeImageBitmap(it) }
    }
    var showLabel by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChoice("Ingredients", selected = showLabel) { showLabel = true }
                    SmallChoice("Front", selected = !showLabel) { showLabel = false }
                }
                Spacer(Modifier.height(10.dp))
                val shown = if (showLabel) label else front
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (shown != null) {
                        Image(shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("No ${if (showLabel) "ingredients" else "front"} photo", color = AppColors.TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SmallChoice(label: String, selected: Boolean, accent: Color = AppColors.Primary, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (selected) accent else Color.White.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (selected) accent else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

data class PickOption(val slot: Int, val label: String, val canAddToList: Boolean)

/** "Which one are you picking?" — one compact sheet for a new or saved comparison. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PickSheet(
    options: List<PickOption>,
    initial: CompareDecision?,
    showAddToList: Boolean,
    saving: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (CompareDecision) -> Unit,
) {
    var decision by remember { mutableStateOf(initial?.decision ?: "picked") }
    var chosen by remember { mutableStateOf(initial?.chosenSlot) }
    var reasons by remember { mutableStateOf(initial?.reasons?.toSet() ?: emptySet()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var addToList by remember { mutableStateOf(true) }
    val chosenOption = options.firstOrNull { it.slot == chosen }
    val valid = decision != "picked" || chosenOption != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.BackgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Which one are you picking?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { opt ->
                    SmallChoice(opt.label, selected = decision == "picked" && chosen == opt.slot) {
                        decision = "picked"; chosen = opt.slot
                    }
                }
                SmallChoice("None", selected = decision == "none", accent = AppColors.TextFaint) {
                    decision = "none"; chosen = null
                }
                SmallChoice("Not yet", selected = decision == "undecided", accent = AppColors.TextFaint) {
                    decision = "undecided"; chosen = null
                }
            }
            Text("Why? (optional)", color = AppColors.TextMuted, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                COMPARE_REASONS.forEach { r ->
                    SmallChoice(r, selected = r in reasons, accent = AppColors.SecondaryLight) {
                        reasons = if (r in reasons) reasons - r else reasons + r
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                placeholder = { Text("Note (optional)", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                ),
            )
            if (showAddToList && decision == "picked" && chosenOption?.canAddToList == true) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { addToList = !addToList }) {
                    Checkbox(
                        checked = addToList,
                        onCheckedChange = { addToList = it },
                        colors = CheckboxDefaults.colors(checkedColor = AppColors.Primary),
                    )
                    Text("Add ${chosenOption.label} to My list", color = Color.White, fontSize = 13.sp)
                }
            }
            Button(
                onClick = {
                    onConfirm(
                        CompareDecision(
                            decision = decision,
                            chosenSlot = if (decision == "picked") chosen else null,
                            reasons = COMPARE_REASONS.filter { it in reasons },
                            note = note,
                            addChosenToShelf = showAddToList && decision == "picked" &&
                                chosenOption?.canAddToList == true && addToList,
                        ),
                    )
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
            ) {
                Text(if (saving) "Saving…" else confirmLabel, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** Rename a product (names and brands are the user's own input). */
@Composable
fun RenameProductDialog(
    title: String,
    initialName: String,
    initialBrand: String?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var brand by remember { mutableStateOf(initialBrand.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = AppColors.BackgroundSecondary, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                listOf(
                    Triple("Name", name) { v: String -> name = v.take(80) },
                    Triple("Brand (optional)", brand) { v: String -> brand = v.take(80) },
                ).forEach { (label, value, set) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = set,
                        label = { Text(label, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        ),
                    )
                }
                Button(
                    onClick = { onSave(name, brand.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                ) { Text("Save", fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}

/** "12 Sep" from an ISO timestamp; blank when missing. */
fun shortDate(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 10) return ""
    val month = iso.substring(5, 7).toIntOrNull() ?: return ""
    val day = iso.substring(8, 10).toIntOrNull() ?: return ""
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "$day ${names.getOrElse(month - 1) { "" }}"
}
