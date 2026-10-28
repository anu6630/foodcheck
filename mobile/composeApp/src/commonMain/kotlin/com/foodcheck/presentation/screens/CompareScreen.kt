package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.data.dto.ComparisonDto
import com.foodcheck.data.dto.ComparisonItemDto
import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.presentation.components.AttachedImageThumb
import com.foodcheck.presentation.state.COMPARE_CATEGORIES
import com.foodcheck.presentation.components.CompactChoiceDialog
import com.foodcheck.presentation.components.CompactChoiceOption
import com.foodcheck.presentation.components.CompactToast
import com.foodcheck.presentation.components.PhotoLoader
import com.foodcheck.presentation.components.RenameProductDialog
import com.foodcheck.presentation.components.SearchHeroBar
import com.foodcheck.presentation.components.SlotThumb
import com.foodcheck.presentation.components.SmallChoice
import com.foodcheck.presentation.components.shortDate
import com.foodcheck.presentation.state.COMPARE_MIN_READY
import com.foodcheck.presentation.state.CompareSlot
import com.foodcheck.presentation.state.CompareViewModel
import com.foodcheck.presentation.state.ComparisonsViewModel
import com.foodcheck.presentation.state.ShelfViewModel
import com.foodcheck.presentation.state.SlotPhotos
import com.foodcheck.presentation.state.slotLetter
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.util.ImagePickerLauncher
import com.foodcheck.util.ImagePickerSource
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** Photo loader for a session slot: captured bytes or a saved photo on the server. */
fun slotPhotoLoader(photos: SlotPhotos, comparisons: ComparisonsViewModel): PhotoLoader =
    { kind -> comparisons.photo(photos, kind) }

/** S1 — Compare tray: 2–3 product slots, then Compare. Tapping an empty slot opens the camera. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareScreen(
    viewModel: CompareViewModel,
    imagePickerLauncher: ImagePickerLauncher,
    onCompare: () -> Unit,
    onOpenComparison: (Int) -> Unit,
    comparisons: ComparisonsViewModel = koinViewModel(),
    shelf: ShelfViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val packs by shelf.items.collectAsState()
    val saved by comparisons.items.collectAsState()
    val scope = rememberCoroutineScope()
    var fixSlot by remember { mutableStateOf<Int?>(null) }
    var menuSlot by remember { mutableStateOf<Int?>(null) }
    var renameSlot by remember { mutableStateOf<Int?>(null) }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }
    var categoryOpen by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        shelf.load("")
        comparisons.load()
    }

    fun capture(slot: Int, source: ImagePickerSource) {
        scope.launch { imagePickerLauncher.pickImage(source)?.let { viewModel.readPhoto(slot, it) } }
    }

    val nextEmpty = state.nextEmptySlot
    val suggestions = packs
        .filter { it.scanId != null && it.ingredients.isNotEmpty() && it.scanId !in state.usedScanIds }
        .take(MY_LIST_SUGGESTIONS)

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Snap the ingredients list of 2–3 packs. Tap a box to open the camera.",
            color = AppColors.TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            state.slots.forEachIndexed { slot, s ->
                SlotTile(
                    slot = slot,
                    state = s,
                    loader = (s as? CompareSlot.Ready)?.let { r ->
                        remember(r.photos) { slotPhotoLoader(r.photos, comparisons) }
                    },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when (s) {
                            CompareSlot.Empty -> capture(slot, ImagePickerSource.CAMERA)
                            is CompareSlot.Failed -> fixSlot = slot
                            else -> menuSlot = slot
                        }
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Or add from", color = AppColors.TextMuted, fontSize = 12.sp)
            TextButton(enabled = nextEmpty != null, onClick = { nextEmpty?.let { capture(it, ImagePickerSource.GALLERY) } }) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Gallery", fontSize = 13.sp)
            }
            TextButton(enabled = nextEmpty != null, onClick = { pickerSlot = nextEmpty }) {
                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("My list", fontSize = 13.sp)
            }
        }

        // Category is read from the label; shown only once there is something to categorise.
        if (state.category != null || state.readySlots.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category  ", color = AppColors.TextMuted, fontSize = 12.sp)
                SmallChoice(
                    state.category?.let { "$it ✎" } ?: "Add",
                    selected = state.category != null,
                    accent = AppColors.SecondaryLight,
                ) { categoryOpen = true }
            }
        }
        state.slots.forEachIndexed { slot, s ->
            if (s is CompareSlot.Failed) {
                IssueLine(
                    text = "${slotLetter(slot)}: ${failTitle(s)}",
                    action = "Fix",
                    onAction = { fixSlot = slot },
                )
            }
            if (s is CompareSlot.Ready && s.maybeIncomplete) {
                IssueLine(
                    text = "${slotLetter(slot)}: ingredients list may be cut off",
                    action = "Retake",
                    onAction = { capture(slot, ImagePickerSource.CAMERA) },
                )
            }
            viewModel.duplicateOf(slot)?.let { other ->
                IssueLine(
                    text = "${slotLetter(slot)} has the same ingredients as ${slotLetter(other)}",
                    action = "Remove",
                    onAction = { viewModel.remove(slot) },
                )
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            if (nextEmpty != null && suggestions.isNotEmpty()) {
                item(key = "h_list") {
                    SectionHeader("From My list", action = "See all") { pickerSlot = nextEmpty }
                }
                items(suggestions, key = { "p${it.id}" }) { item ->
                    QuickAddRow(
                        title = item.displayName,
                        subtitle = listOfNotNull(
                            item.brand,
                            "${item.ingredients.count { it.severity in setOf("red", "amber") || it.showsWatch }} ⚑",
                        ).joinToString(" · "),
                        onAdd = {
                            viewModel.fillFromShelf(nextEmpty, item)
                            toastMessage = "Added ${item.displayName} to Slot ${slotLetter(nextEmpty)}"
                        },
                    )
                }
            }
            if (suggestions.isEmpty() && saved.isEmpty()) {
                item(key = "tips") { PhotoTips() }
            }
            if (saved.isNotEmpty()) {
                item(key = "h_recent") { SectionHeader("Recent comparisons") }
                items(saved.take(RECENT_COMPARISONS), key = { "c${it.id}" }) { c ->
                    val picked = c.items.firstOrNull { it.slot == c.chosenSlot }
                    PickerRow(
                        title = c.items.sortedBy { it.slot }.joinToString(" vs ") { it.displayName },
                        subtitle = listOfNotNull(
                            c.category,
                            when (c.decision) {
                                "picked" -> "picked ${picked?.displayName ?: slotLetter(c.chosenSlot ?: 0)}"
                                "none" -> "picked none"
                                else -> null
                            },
                            shortDate(c.createdAt).ifBlank { null },
                        ).joinToString(" · "),
                        disabledReason = null,
                        onClick = { onOpenComparison(c.id) },
                    )
                }
            }
        }

        val ready = state.readySlots.size
        Button(
            onClick = onCompare,
            enabled = state.canCompare,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
        ) {
            Text(
                when {
                    state.hasErrors -> "Fix the product marked ⚠"
                    state.busy -> "Reading labels…"
                    ready < COMPARE_MIN_READY -> "Add ${COMPARE_MIN_READY - ready} more"
                    else -> "Compare $ready products"
                },
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    fixSlot?.let { slot ->
        val failed = state.slots[slot] as? CompareSlot.Failed
        if (failed == null) {
            fixSlot = null
        } else {
            FixProductSheet(
                slot = slot,
                failed = failed,
                imagePickerLauncher = imagePickerLauncher,
                onLabel = {
                    viewModel.fixWithPhoto(slot, it)
                    fixSlot = null
                },
                onRetry = {
                    viewModel.retry(slot)
                    fixSlot = null
                },
                onRemove = {
                    viewModel.remove(slot)
                    fixSlot = null
                },
                onDismiss = { fixSlot = null },
            )
        }
    }
    menuSlot?.let { slot ->
        val s = state.slots[slot]
        CompactChoiceDialog(
            title = "Product ${slotLetter(slot)}",
            options = buildList {
                if (s is CompareSlot.Ready) add(CompactChoiceOption("rename", "Rename", AppColors.Primary))
                add(CompactChoiceOption("retake", "Retake photo", AppColors.SecondaryLight))
                add(CompactChoiceOption("remove", "Remove", AppColors.Red))
            },
            onSelect = { choice ->
                menuSlot = null
                when (choice.id) {
                    "rename" -> renameSlot = slot
                    "retake" -> capture(slot, ImagePickerSource.CAMERA)
                    "remove" -> viewModel.remove(slot)
                }
            },
            onDismiss = { menuSlot = null },
        )
    }
    renameSlot?.let { slot ->
        val r = state.slots[slot] as? CompareSlot.Ready
        if (r == null) renameSlot = null else RenameProductDialog(
            title = "Product ${slotLetter(slot)}",
            initialName = r.name,
            initialBrand = r.brand,
            onDismiss = { renameSlot = null },
            onSave = { n, b ->
                viewModel.rename(slot, n, b)
                renameSlot = null
            },
        )
    }
    pickerSlot?.let { slot ->
        MyListPickerSheet(
            usedScanIds = state.usedScanIds,
            comparisons = comparisons,
            shelf = shelf,
            onPickShelf = {
                viewModel.fillFromShelf(slot, it)
                toastMessage = "Added ${it.displayName} to Slot ${slotLetter(slot)}"
                pickerSlot = null
            },
            onPickSaved = { comparisonId, item ->
                viewModel.fillFromSavedComparison(slot, comparisonId, item)
                toastMessage = "Added ${item.displayName} to Slot ${slotLetter(slot)}"
                pickerSlot = null
            },
            onDismiss = { pickerSlot = null },
        )
    }
    if (categoryOpen) {
        CompactChoiceDialog(
            title = "What are you comparing?",
            options = (listOf("none" to "No category") + COMPARE_CATEGORIES.map { it to it }).map { (id, label) ->
                CompactChoiceOption(
                    id = id,
                    label = label,
                    accent = AppColors.SecondaryLight,
                    selected = (state.category ?: "none") == id,
                )
            },
            onSelect = {
                viewModel.setCategory(it.id.takeUnless { id -> id == "none" })
                categoryOpen = false
            },
            onDismiss = { categoryOpen = false },
        )
    }

    CompactToast(
        message = toastMessage.orEmpty(),
        visible = toastMessage != null,
        onDismiss = { toastMessage = null },
    )
    }
}

/** First-run guidance: a clear photo is what makes the read succeed. */
@Composable
private fun PhotoTips() {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("For a good read", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        listOf(
            "Fill the photo with the ingredients list",
            "Hold steady, avoid glare on shiny packs",
            "One pack per box",
        ).forEach { Text("•  $it", color = AppColors.TextMuted, fontSize = 12.sp) }
    }
}

private const val MY_LIST_SUGGESTIONS = 4
private const val RECENT_COMPARISONS = 3

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = onAction) { Text(action, color = AppColors.Primary, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun QuickAddRow(title: String, subtitle: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = AppColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.Primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, contentDescription = "Add $title", tint = AppColors.Primary, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun SlotTile(
    slot: Int,
    state: CompareSlot,
    loader: PhotoLoader?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val letter = slotLetter(slot)
    val border = when (state) {
        is CompareSlot.Failed -> AppColors.Red
        is CompareSlot.Ready -> AppColors.Primary.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    Surface(
        modifier = modifier.height(150.dp).clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    CompareSlot.Empty -> Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, contentDescription = "Add product $letter", tint = AppColors.Primary) }
                    is CompareSlot.Reading -> {
                        AttachedImageThumb(bytes = state.photos.first(), sizeDp = 56)
                        CircularProgressIndicator(Modifier.size(26.dp), color = AppColors.Primary, strokeWidth = 2.dp)
                    }
                    is CompareSlot.Loading -> CircularProgressIndicator(Modifier.size(26.dp), color = AppColors.Primary, strokeWidth = 2.dp)
                    is CompareSlot.Ready -> SlotThumb(loader = loader!!, letter = letter, size = 56.dp)
                    is CompareSlot.Failed -> Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.Red, modifier = Modifier.size(28.dp))
                }
            }
            val (title, sub) = when (state) {
                CompareSlot.Empty -> letter to if (slot < COMPARE_MIN_READY) "Add" else "Optional"
                is CompareSlot.Reading -> letter to "Reading…"
                is CompareSlot.Loading -> state.name to "Loading…"
                is CompareSlot.Ready -> state.name to buildString {
                    append("${state.ingredients.size} ingr.")
                    if (state.fromMyList) shortDate(state.labelDate).takeIf { it.isNotBlank() }?.let { append(" · $it") }
                }
                is CompareSlot.Failed -> letter to "Fix needed"
            }
            val named = state is CompareSlot.Ready || state is CompareSlot.Loading
            if (named) Text(letter, color = AppColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (named) 2 else 1,
                lineHeight = 14.sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                sub,
                color = if (state is CompareSlot.Failed) AppColors.Red else AppColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun failTitle(failed: CompareSlot.Failed): String = when (failed.reason) {
    CompareSlot.FailReason.FrontOnly -> "only the front is visible"
    CompareSlot.FailReason.NoIngredients -> "no ingredients found"
    CompareSlot.FailReason.Network -> "couldn't read the label"
}

@Composable
private fun IssueLine(text: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.Amber, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = AppColors.TextFaint, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action, color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixProductSheet(
    slot: Int,
    failed: CompareSlot.Failed,
    imagePickerLauncher: ImagePickerLauncher,
    onLabel: (ByteArray) -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.BackgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Product ${slotLetter(slot)}: ${failTitle(failed)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                failed.photos.forEach { AttachedImageThumb(bytes = it, sizeDp = 48); Spacer(Modifier.width(6.dp)) }
                Spacer(Modifier.width(4.dp))
                Text(
                    when (failed.reason) {
                        CompareSlot.FailReason.FrontOnly -> "We can see the front. Add a photo of the ingredients list."
                        CompareSlot.FailReason.NoIngredients -> "No ingredients list found — it may be blurry, cut off, or the wrong side."
                        CompareSlot.FailReason.Network -> failed.message
                    },
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = { scope.launch { imagePickerLauncher.pickImage(ImagePickerSource.CAMERA)?.let(onLabel) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
            ) {
                Text(
                    if (failed.reason == CompareSlot.FailReason.FrontOnly) "Add ingredients photo" else "Retake ingredients photo",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            OutlinedButton(
                onClick = { scope.launch { imagePickerLauncher.pickImage(ImagePickerSource.GALLERY)?.let(onLabel) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Choose from gallery", color = Color.White) }
            if (failed.reason == CompareSlot.FailReason.Network && failed.photos.isNotEmpty()) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Try again", color = Color.White)
                }
            }
            TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                Text("Remove product ${slotLetter(slot)}", color = AppColors.Red)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyListPickerSheet(
    usedScanIds: Set<Int>,
    comparisons: ComparisonsViewModel,
    onPickShelf: (ShelfItemDto) -> Unit,
    onPickSaved: (Int, ComparisonItemDto) -> Unit,
    onDismiss: () -> Unit,
    shelf: ShelfViewModel,
) {
    val packs by shelf.items.collectAsState()
    val query by shelf.query.collectAsState()
    val saved by comparisons.items.collectAsState()
    var fromComparisons by remember { mutableStateOf(false) }
    // The tray shares this list; don't leave it filtered by the picker's search.
    DisposableEffect(Unit) { onDispose { shelf.setQuery("") } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.BackgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Pick from My list", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallChoice("Packs", selected = !fromComparisons) { fromComparisons = false }
                SmallChoice("From comparisons", selected = fromComparisons) { fromComparisons = true }
            }
            Spacer(Modifier.height(8.dp))
            if (!fromComparisons) {
                SearchHeroBar(query = query, onQueryChange = { shelf.setQuery(it) }, placeholder = "Name, brand, ingredient", showScopeHints = false)
                Spacer(Modifier.height(6.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (!fromComparisons) {
                    if (packs.isEmpty()) item { EmptyPickerText("No saved packs yet") }
                    items(packs, key = { "p${it.id}" }) { item ->
                        val used = item.scanId != null && item.scanId in usedScanIds
                        val noIngredients = item.ingredients.isEmpty() || item.scanId == null
                        PickerRow(
                            title = item.displayName,
                            subtitle = listOfNotNull(
                                item.brand,
                                shortDate(item.createdAt).ifBlank { null },
                                "${item.ingredients.count { it.severity in setOf("red", "amber") || it.showsWatch }} ⚑",
                            ).joinToString(" · "),
                            disabledReason = when {
                                used -> "Already in this comparison"
                                noIngredients -> "No ingredients saved"
                                else -> null
                            },
                            onClick = { onPickShelf(item) },
                        )
                    }
                } else {
                    val rows = saved.flatMap { c -> c.items.map { c to it } }
                    if (rows.isEmpty()) item { EmptyPickerText("No saved comparisons yet") }
                    items(rows, key = { (c, i) -> "c${c.id}_${i.slot}" }) { (c, i) ->
                        PickerRow(
                            title = i.displayName,
                            subtitle = listOfNotNull(
                                c.category,
                                shortDate(c.createdAt).ifBlank { null },
                                "${i.flaggedCount} ⚑",
                            ).joinToString(" · "),
                            disabledReason = when {
                                i.scanId in usedScanIds -> "Already in this comparison"
                                i.ingredientCount == 0 -> "No ingredients saved"
                                else -> null
                            },
                            onClick = { onPickSaved(c.id, i) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPickerText(text: String) {
    Text(text, color = AppColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun PickerRow(title: String, subtitle: String, disabledReason: String?, onClick: () -> Unit) {
    val enabled = disabledReason == null
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Text(
            title,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            disabledReason ?: subtitle,
            color = AppColors.TextMuted.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Saved comparisons inside My list. */
@Composable
fun ComparisonListItems(
    items: List<ComparisonDto>,
    onOpen: (ComparisonDto) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { c ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(c) },
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOfNotNull(c.category ?: "Comparison", c.tags.joinToString(", ").ifBlank { null }).joinToString(" · "),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(shortDate(c.createdAt), color = AppColors.TextMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    val picked = c.items.firstOrNull { it.slot == c.chosenSlot }
                    Text(
                        when (c.decision) {
                            "picked" -> "Picked: ${picked?.displayName ?: slotLetter(c.chosenSlot ?: 0)}"
                            "none" -> "Picked none"
                            else -> "Not decided"
                        },
                        color = if (c.decision == "picked") AppColors.Primary else AppColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        c.items.joinToString("   ") { i ->
                            val mark = if (i.slot == c.chosenSlot) "✓" else ""
                            "${slotLetter(i.slot)}$mark ${i.displayName.take(14)} ${i.flaggedCount}⚑"
                        },
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
