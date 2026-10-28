package com.foodcheck.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.ComparisonMatrix
import com.foodcheck.presentation.components.CompactChoiceDialog
import com.foodcheck.presentation.components.CompactChoiceOption
import com.foodcheck.presentation.components.CompactMessageDialog
import com.foodcheck.presentation.components.CompactToast
import com.foodcheck.presentation.components.ComparisonColumn
import com.foodcheck.presentation.components.ComparisonHeader
import com.foodcheck.presentation.components.MatrixSection
import com.foodcheck.presentation.components.PickOption
import com.foodcheck.presentation.components.PickSheet
import com.foodcheck.presentation.components.ProductPhotosDialog
import com.foodcheck.presentation.components.RenameProductDialog
import com.foodcheck.presentation.components.comparisonSections
import com.foodcheck.presentation.components.shortDate
import com.foodcheck.presentation.state.CompareDecision
import com.foodcheck.presentation.state.CompareSlot
import com.foodcheck.presentation.state.CompareViewModel
import com.foodcheck.presentation.state.ComparisonsViewModel
import com.foodcheck.presentation.state.SlotPhotos
import com.foodcheck.presentation.state.slotLetter
import com.foodcheck.presentation.theme.AppColors
import org.koin.compose.viewmodel.koinViewModel

private fun defaultSections(matrix: ComparisonMatrix): Set<MatrixSection> =
    if (matrix.flagged.isNotEmpty()) setOf(MatrixSection.Flagged) else setOf(MatrixSection.Flagged, MatrixSection.Main)

/** S4 + S5 — grouped comparison of the current session, then pick & save. */
@Composable
fun CompareResultScreen(
    viewModel: CompareViewModel,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    onSaved: (id: Int, first: Boolean) -> Unit,
    comparisons: ComparisonsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val matrix by viewModel.matrix.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var renameSlot by remember { mutableStateOf<Int?>(null) }
    var photosSlot by remember { mutableStateOf<Int?>(null) }

    // Empty only after a successful save resets the session, while navigating to the saved
    // comparison — render nothing rather than navigating again.
    val m = matrix ?: return
    var expanded by remember { mutableStateOf(defaultSections(m)) }
    val readySlots = state.readySlots
    val columns = readySlots.mapIndexed { index, slot ->
        val r = state.slots[slot] as CompareSlot.Ready
        ComparisonColumn(
            slot = slot,
            letter = slotLetter(slot),
            name = r.name,
            summary = m.products[index],
            photoLoader = remember(r.photos) { slotPhotoLoader(r.photos, comparisons) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            listOfNotNull(state.category, state.tags.joinToString(", ").ifBlank { null }).joinToString(" · ")
                .ifBlank { "Comparison" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ComparisonHeader(
            columns = columns,
            onColumnClick = { renameSlot = it.slot },
            onPhotoClick = { photosSlot = it.slot },
            hint = "Tap a name to rename",
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            comparisonSections(
                matrix = m,
                letters = columns.map { it.letter },
                expanded = expanded,
                onToggle = { s -> expanded = if (s in expanded) expanded - s else expanded + s },
                onIngredientClick = onIngredientClick,
            )
            item { LabelNote() }
        }
        Button(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
        ) { Text("Which one are you picking?", fontWeight = FontWeight.Bold, color = Color.White) }
    }

    if (picking) {
        PickSheet(
            options = readySlots.map { slot ->
                val r = state.slots[slot] as CompareSlot.Ready
                PickOption(slot, "${slotLetter(slot)} · ${r.name}", canAddToList = !r.fromMyList)
            },
            initial = null,
            showAddToList = true,
            saving = state.saving,
            confirmLabel = "Save comparison",
            onDismiss = { if (!state.saving) picking = false },
            onConfirm = { decision ->
                com.foodcheck.analytics.getAnalyticsTracker().logCompareDecision(
                    decision = decision.decision,
                    chosenSlot = decision.chosenSlot,
                    addChosenToShelf = decision.addChosenToShelf
                )
                viewModel.save(decision) { id, first -> picking = false; onSaved(id, first) }
            },
        )
    }
    renameSlot?.let { slot ->
        val r = state.slots[slot] as? CompareSlot.Ready
        if (r != null) RenameProductDialog(
            title = "Product ${slotLetter(slot)}",
            initialName = r.name,
            initialBrand = r.brand,
            onDismiss = { renameSlot = null },
            onSave = { n, b -> viewModel.rename(slot, n, b); renameSlot = null },
        )
    }
    photosSlot?.let { slot ->
        val r = state.slots[slot] as? CompareSlot.Ready
        if (r != null) ProductPhotosDialog(
            title = "${slotLetter(slot)} · ${r.name}",
            loader = remember(r.photos) { slotPhotoLoader(r.photos, comparisons) },
            onDismiss = { photosSlot = null },
        )
    }
    state.message?.let { msg ->
        CompactMessageDialog(title = "Not saved", message = msg, onDismiss = { viewModel.clearMessage() })
    }
}

@Composable
private fun LabelNote() {
    Text(
        "#n = position on the label (#1 is the main ingredient). ⚑ = restricted, banned or health-watch for India. " +
            "Based on the labels you photographed; not medical advice.",
        color = AppColors.TextMuted.copy(alpha = 0.7f),
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Saved comparison: same table, the pick, and change / redo / delete. */
@Composable
fun ComparisonDetailScreen(
    id: Int,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    onRedo: () -> Unit,
    onDeleted: () -> Unit,
    compareViewModel: CompareViewModel,
    viewModel: ComparisonsViewModel = koinViewModel(),
) {
    val state by viewModel.detail.collectAsState()
    LaunchedEffect(id) { viewModel.open(id) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var photosSlot by remember { mutableStateOf<Int?>(null) }

    val detail = state.detail
    val m = state.matrix
    if (detail == null || m == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.loading) CircularProgressIndicator(color = AppColors.Primary)
            else Text(state.error ?: "Comparison unavailable", color = AppColors.TextMuted)
        }
        return
    }
    var expanded by remember(detail.id) { mutableStateOf(defaultSections(m)) }
    val items = detail.items.sortedBy { it.slot }
    val loaders = items.associate { item ->
        item.slot to remember(detail.id, item.slot) {
            slotPhotoLoader(SlotPhotos.Saved(detail.id, item.slot, item.hasFront, item.hasLabel), viewModel)
        }
    }
    val columns = items.mapIndexed { index, item ->
        ComparisonColumn(
            slot = item.slot,
            letter = slotLetter(item.slot),
            name = item.displayName,
            summary = m.products[index],
            photoLoader = loaders.getValue(item.slot),
            chosen = detail.decision == "picked" && detail.chosenSlot == item.slot,
        )
    }
    val picked = items.firstOrNull { it.slot == detail.chosenSlot }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                listOfNotNull(detail.category ?: "Comparison", detail.tags.joinToString(", ").ifBlank { null }, shortDate(detail.createdAt).ifBlank { null })
                    .joinToString(" · "),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                when (detail.decision) {
                    "picked" -> "✓ You picked ${slotLetter(detail.chosenSlot ?: 0)} · ${picked?.displayName.orEmpty()}"
                    "none" -> "You picked none"
                    else -> "Not decided yet"
                } + detail.reasons.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " — ").orEmpty(),
                color = if (detail.decision == "picked") AppColors.Primary else AppColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            detail.note?.let { Text("“$it”", color = AppColors.TextFaint, fontSize = 12.sp) }
        }
        ComparisonHeader(
            columns = columns,
            onColumnClick = { photosSlot = it.slot },
            onPhotoClick = { photosSlot = it.slot },
            hint = "Tap a photo to view",
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            comparisonSections(
                matrix = m,
                letters = columns.map { it.letter },
                expanded = expanded,
                onToggle = { s -> expanded = if (s in expanded) expanded - s else expanded + s },
                onIngredientClick = onIngredientClick,
            )
            item { LabelNote() }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val pad = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            Button(
                onClick = { picking = true },
                modifier = Modifier.weight(1f),
                contentPadding = pad,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
            ) { Text("Change pick", color = Color.White, fontSize = 13.sp, maxLines = 1) }
            OutlinedButton(
                onClick = { compareViewModel.startFromSaved(detail); onRedo() },
                modifier = Modifier.weight(1f),
                contentPadding = pad,
                shape = RoundedCornerShape(12.dp),
            ) { Text("Swap one", color = Color.White, fontSize = 13.sp, maxLines = 1) }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(0.8f),
                contentPadding = pad,
                shape = RoundedCornerShape(12.dp),
            ) { Text("Delete", color = AppColors.Red, fontSize = 13.sp, maxLines = 1) }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (picking) {
        PickSheet(
            options = items.map { PickOption(it.slot, "${slotLetter(it.slot)} · ${it.displayName}", canAddToList = false) },
            initial = CompareDecision(detail.decision, detail.chosenSlot, detail.reasons, detail.note, false),
            showAddToList = false,
            saving = state.busy,
            confirmLabel = "Save",
            onDismiss = { picking = false },
            onConfirm = { viewModel.updateDecision(it); picking = false },
        )
    }
    if (confirmDelete) {
        CompactChoiceDialog(
            title = "Delete this comparison?",
            options = listOf(
                CompactChoiceOption("delete", "Delete (My list packs stay)", AppColors.Red),
                CompactChoiceOption("keep", "Keep", AppColors.TextMuted),
            ),
            onSelect = { choice ->
                confirmDelete = false
                if (choice.id == "delete") viewModel.delete(onDeleted)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    photosSlot?.let { slot ->
        val item = items.firstOrNull { it.slot == slot }
        if (item != null) ProductPhotosDialog(
            title = "${slotLetter(slot)} · ${item.displayName}",
            loader = loaders.getValue(slot),
            onDismiss = { photosSlot = null },
        )
    }
    state.message?.let { msg ->
        CompactToast(message = msg, visible = true, onDismiss = { viewModel.clearMessage() })
    }
}
