package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.foodcheck.presentation.state.slotLetter
import com.foodcheck.presentation.components.CompactChoiceOption
import com.foodcheck.presentation.components.CompactChoiceDialog
import com.foodcheck.FeatureFlags
import com.foodcheck.data.dto.CatalogBrandDto
import com.foodcheck.data.dto.CatalogCompanyDto
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.SHELF_TAG_OPTIONS
import com.foodcheck.domain.entities.ScanCandidate
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.entities.ShelfStance
import com.foodcheck.domain.entities.labelRegulationSummary
import com.foodcheck.presentation.components.CompactMessageDialog
import com.foodcheck.presentation.components.CompactSavingOverlay
import com.foodcheck.presentation.components.CompactToast
import com.foodcheck.presentation.components.LabelIngredientChips
import com.foodcheck.presentation.components.LabelSummaryStrip
import com.foodcheck.ads.AdsController
import com.foodcheck.presentation.navigation.HandleBack
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSuggestionsScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onCandidateClick: (ScanCandidate) -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    /** Name per compare slot (null = free), so "Add to compare" can say what is waiting. */
    compareSlots: List<String?>,
    /** Adds this read to the compare tray; [replaceSlot] when the tray was full. */
    onAddToCompare: (replaceSlot: Int?) -> Unit,
) {
    val outcome by viewModel.outcome.collectAsState()
    var replaceOpen by remember { mutableStateOf(false) }
    val actionMessage by viewModel.actionMessage.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val ingredients = outcome?.labelIngredients().orEmpty()
    val summary = remember(ingredients) { labelRegulationSummary(ingredients) }
    val similar = when (val o = outcome) {
        is ScanOutcome.Suggestions -> o.candidates
        is ScanOutcome.Confident -> emptyList()
        else -> emptyList()
    }
    var saveOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveBrand by remember { mutableStateOf("") }
    var saveBrandId by remember { mutableStateOf<Int?>(null) }
    var selectedCompanies by remember { mutableStateOf<List<CatalogCompanyDto>>(emptyList()) }
    var saveStance by remember { mutableStateOf(ShelfStance.Unsure) }
    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var caption by remember { mutableStateOf("") }
    var isSaved by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val brandSuggestions by viewModel.brandSuggestions.collectAsState()
    val companySuggestions by viewModel.companySuggestions.collectAsState()
    val isIos = currentPlatform == Platform.IOS

    HandleBack(enabled = !saving, onBack = onBack)

    LaunchedEffect(outcome) {
        outcome?.let {
            com.foodcheck.analytics.getAnalyticsTracker().logScanSuggestionsViewed(
                candidateCount = similar.size,
                flaggedCount = summary.banned + summary.restricted + summary.watch
            )
        }
    }

    // After a successful save: show green confirmation in sheet, toast, then clear scan.
    LaunchedEffect(actionMessage, saving) {
        val msg = actionMessage ?: return@LaunchedEffect
        if (saving) return@LaunchedEffect
        if (msg == "Saved to your list") {
            isSaved = true
            delay(500)
            saveOpen = false
            toastMessage = msg
            viewModel.clearActionMessage()
            delay(1200)
            // Task finished: a natural break for a full-screen ad.
            AdsController.showInterstitial { onBack() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Global LifeX bar is hidden on this route; iOS needs explicit inset.
            .then(if (isIos) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = if (isIos) {
                    Modifier.size(38.dp)
                } else {
                    Modifier.background(AppColors.Surface, shape = RoundedCornerShape(12.dp)).size(38.dp)
                }
            ) {
                Icon(
                    imageVector = if (isIos) Icons.Default.ChevronLeft else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(if (isIos) 28.dp else 18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "From this label",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (ingredients.isEmpty()) {
                        "No substances read"
                    } else {
                        val n = summary.substanceCount
                        "Market ${summary.marketCode} · $n substance${if (n == 1) "" else "s"}"
                    },
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (ingredients.isEmpty()) {
                item {
                    Text(
                        "No ingredients could be read from the photo.",
                        color = AppColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                item { LabelSummaryStrip(ingredients) }
                item {
                    Text(
                        "Watch tags are health signals — not the same as banned.",
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
                item {
                    LabelIngredientChips(
                        ingredients = ingredients,
                        onIngredientClick = onIngredientClick
                    )
                }
            }
            if (FeatureFlags.socialFeedEnabled) {
                item {
                    TextButton(onClick = { shareOpen = true }) {
                        Text("Share as story", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (similar.isNotEmpty()) {
                item {
                    Text("Similar packs", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text(
                        "These are similar packs in the catalog — not a confirmed match.",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
                items(similar) { candidate ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AppColors.Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable {
                                com.foodcheck.analytics.getAnalyticsTracker().logSearchResultSelected("scan_candidate", candidate.product.barcode, candidate.product.name)
                                onCandidateClick(candidate)
                            },
                        color = AppColors.Surface.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.product.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Brand: ${candidate.product.brand ?: "Unknown"}", color = Color.Gray, fontSize = 12.sp)
                            }
                            Text("${candidate.matchScore}%", color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ingredients.isNotEmpty()) {
                val waiting = compareSlots.count { it != null }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        com.foodcheck.analytics.getAnalyticsTracker().logScanAddToCompare(if (waiting == compareSlots.size) -1 else waiting)
                        if (waiting == compareSlots.size) replaceOpen = true else onAddToCompare(null)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Add to compare", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        if (waiting > 0) Text("$waiting waiting", color = AppColors.TextMuted, fontSize = 11.sp)
                    }
                }
            }
            Button(
                onClick = {
                    if (saveName.isBlank()) saveName = outcome?.suggestedName().orEmpty()
                    if (saveBrand.isBlank()) saveBrand = outcome?.suggestedBrand().orEmpty()
                    saveOpen = true
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
            ) {
                Text("Save to my list", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }

    if (saveOpen) {
        SaveToListBottomSheet(
            name = saveName,
            brand = saveBrand,
            stance = saveStance,
            selectedTags = selectedTags,
            brandSuggestions = brandSuggestions,
            companySuggestions = companySuggestions,
            selectedCompanies = selectedCompanies,
            onNameChange = { saveName = it },
            onBrandChange = { text ->
                saveBrand = text
                saveBrandId = null
                viewModel.searchBrands(text)
            },
            onBrandPick = { picked ->
                saveBrand = picked.name
                saveBrandId = picked.id
                picked.companyId?.let { cid ->
                    val cname = picked.companyName ?: return@let
                    if (selectedCompanies.none { it.id == cid }) {
                        selectedCompanies = selectedCompanies + CatalogCompanyDto(cid, cname)
                    }
                }
                viewModel.searchBrands(picked.name)
            },
            onStanceChange = { saveStance = it },
            onTagToggle = { tag ->
                selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
            },
            onCompanyQuery = { viewModel.searchCompanies(it) },
            onCompanyToggle = { company ->
                selectedCompanies = if (selectedCompanies.any { it.id == company.id }) {
                    selectedCompanies.filterNot { it.id == company.id }
                } else {
                    selectedCompanies + company
                }
            },
            onCompanyRemove = { id ->
                selectedCompanies = selectedCompanies.filterNot { it.id == id }
            },
            saving = saving,
            isSaved = isSaved,
            onSave = {
                viewModel.saveToShelf(
                    displayName = saveName,
                    brand = saveBrand,
                    brandId = saveBrandId,
                    companyIds = selectedCompanies.map { it.id },
                    stance = saveStance.apiValue,
                    tags = selectedTags,
                )
            },
            onClose = { if (!saving) saveOpen = false },
            onOpen = {
                isSaved = false
                viewModel.searchBrands(saveBrand)
                viewModel.searchCompanies("")
            },
        )
    }
    if (FeatureFlags.socialFeedEnabled && shareOpen) {
        ShareStoryBottomSheet(
            caption = caption,
            onCaptionChange = { caption = it },
            onPublish = {
                viewModel.publishStory(caption)
                shareOpen = false
                caption = ""
            },
            onClose = { shareOpen = false },
        )
    }
    if (replaceOpen) {
        CompactChoiceDialog(
            title = "Compare is full. Replace which one?",
            options = compareSlots.mapIndexed { i, name ->
                CompactChoiceOption(i.toString(), "${slotLetter(i)} · ${name.orEmpty()}", AppColors.SecondaryLight)
            },
            onSelect = {
                replaceOpen = false
                onAddToCompare(it.id.toInt())
            },
            onDismiss = { replaceOpen = false },
        )
    }
    CompactSavingOverlay(visible = saving && !saveOpen)
    CompactToast(
        message = toastMessage.orEmpty(),
        visible = toastMessage != null,
        onDismiss = { toastMessage = null },
    )
    // Errors (or non-success messages) use compact dialog — success uses toast + exit.
    actionMessage?.takeIf { it != "Saved to your list" }?.let { msg ->
        CompactMessageDialog(
            title = "Couldn’t save",
            message = msg,
            confirmLabel = "OK",
            onDismiss = { viewModel.clearActionMessage() },
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SaveToListBottomSheet(
    name: String,
    brand: String,
    stance: ShelfStance,
    selectedTags: List<String>,
    brandSuggestions: List<CatalogBrandDto>,
    companySuggestions: List<CatalogCompanyDto>,
    selectedCompanies: List<CatalogCompanyDto>,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onBrandPick: (CatalogBrandDto) -> Unit,
    onStanceChange: (ShelfStance) -> Unit,
    onTagToggle: (String) -> Unit,
    onCompanyQuery: (String) -> Unit,
    onCompanyToggle: (CatalogCompanyDto) -> Unit,
    onCompanyRemove: (Int) -> Unit,
    saving: Boolean = false,
    isSaved: Boolean = false,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
) {
    var showCompany by remember { mutableStateOf(selectedCompanies.isNotEmpty()) }
    var showTags by remember { mutableStateOf(selectedTags.isNotEmpty()) }
    var companyQuery by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { onOpen() }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.BackgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Save to my list",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Your stance colors this pack on My list.",
                        fontSize = 12.sp,
                        color = AppColors.TextMuted,
                    )
                }
                IconButton(
                    onClick = { if (!saving) onClose() },
                    enabled = !saving,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            SheetTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Name this pack",
            )
            SheetTextField(
                value = brand,
                onValueChange = onBrandChange,
                label = "Brand",
            )
            SuggestionChipRow(
                labels = brandSuggestions.map { it.name },
                onPick = { label ->
                    brandSuggestions.firstOrNull { it.name == label }?.let(onBrandPick)
                },
            )

            Text("Stance", fontSize = 12.sp, color = AppColors.TextMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShelfStance.entries.forEach { option ->
                    val selected = option == stance
                    Surface(
                        modifier = Modifier.clickable { onStanceChange(option) },
                        color = if (selected) stanceColor(option).copy(alpha = 0.28f) else AppColors.Surface.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) stanceColor(option) else AppColors.Border.copy(alpha = 0.35f),
                        ),
                    ) {
                        Text(
                            option.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White,
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!showCompany) {
                    OptionalAddChip("Company") {
                        showCompany = true
                        onCompanyQuery("")
                    }
                }
                if (!showTags) {
                    OptionalAddChip("Tag") { showTags = true }
                }
            }

            if (showCompany) {
                Text("Related companies (optional)", fontSize = 12.sp, color = AppColors.TextMuted)
                SheetTextField(
                    value = companyQuery,
                    onValueChange = {
                        companyQuery = it
                        onCompanyQuery(it)
                    },
                    label = "Search companies",
                )
                SuggestionChipRow(
                    labels = companySuggestions
                        .filter { c -> selectedCompanies.none { it.id == c.id } }
                        .map { it.name },
                    onPick = { label ->
                        companySuggestions.firstOrNull { it.name == label }?.let(onCompanyToggle)
                    },
                )
                if (selectedCompanies.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedCompanies.forEach { company ->
                            SelectedChip(label = company.name, onRemove = { onCompanyRemove(company.id) })
                        }
                    }
                }
            }

            if (showTags) {
                Text("Tags (optional)", fontSize = 12.sp, color = AppColors.TextMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SHELF_TAG_OPTIONS.forEach { tag ->
                        val selected = tag in selectedTags
                        Surface(
                            modifier = Modifier.clickable { onTagToggle(tag) },
                            color = if (selected) AppColors.Primary.copy(alpha = 0.22f) else AppColors.Surface.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) AppColors.Primary.copy(alpha = 0.5f) else AppColors.Border.copy(alpha = 0.35f),
                            ),
                        ) {
                            Text(
                                tag,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                fontSize = 12.sp,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onSave,
                enabled = name.isNotBlank() && !saving && !isSaved,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaved) Color(0xFF10B981) else AppColors.Primary,
                    disabledContainerColor = if (isSaved) Color(0xFF10B981) else AppColors.Primary.copy(alpha = 0.5f),
                ),
            ) {
                when {
                    isSaved -> {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saved to My List!", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    saving -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Saving to list…", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    else -> {
                        Text("Save to My List", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun stanceColor(stance: ShelfStance): Color = when (stance) {
    ShelfStance.Avoid -> Color(0xFFEF4444)
    ShelfStance.Occasional -> Color(0xFFF59E0B)
    ShelfStance.Ok -> AppColors.Primary
    ShelfStance.Unsure -> Color(0xFF94A3B8)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChipRow(
    labels: List<String>,
    onPick: (String) -> Unit,
) {
    if (labels.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.take(6).forEach { label ->
            Surface(
                modifier = Modifier.clickable { onPick(label) },
                color = AppColors.Surface.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.35f)),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun OptionalAddChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = AppColors.Primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.Primary)
        }
    }
}

@Composable
private fun SelectedChip(label: String, onRemove: () -> Unit) {
    Surface(
        color = AppColors.Primary.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 12.sp, color = Color.White)
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareStoryBottomSheet(
    caption: String,
    onCaptionChange: (String) -> Unit,
    onPublish: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.BackgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Share as story",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Posts this label in the app. Not an official product entry.",
                        fontSize = 12.sp,
                        color = AppColors.TextMuted,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SheetTextField(
                value = caption,
                onValueChange = onCaptionChange,
                label = "Caption (optional)",
                minLines = 2,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onPublish,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
            ) {
                Text("Publish story", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = minLines == 1,
        minLines = minLines,
        maxLines = if (minLines > 1) 4 else 1,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.Primary,
            unfocusedBorderColor = AppColors.Border.copy(alpha = 0.4f),
            focusedLabelColor = AppColors.Primary,
            unfocusedLabelColor = AppColors.TextMuted,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = AppColors.Primary,
            focusedContainerColor = AppColors.Surface.copy(alpha = 0.35f),
            unfocusedContainerColor = AppColors.Surface.copy(alpha = 0.2f),
        ),
    )
}
