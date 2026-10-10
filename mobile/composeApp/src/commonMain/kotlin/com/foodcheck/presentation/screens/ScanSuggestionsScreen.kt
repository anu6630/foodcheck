package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.foodcheck.FeatureFlags
import com.foodcheck.data.dto.CatalogBrandDto
import com.foodcheck.data.dto.CatalogCompanyDto
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.ScanCandidate
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.entities.labelRegulationSummary
import com.foodcheck.presentation.components.LabelIngredientChips
import com.foodcheck.presentation.components.LabelSummaryStrip
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSuggestionsScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onCandidateClick: (ScanCandidate) -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit
) {
    val outcome by viewModel.outcome.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
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
    var caption by remember { mutableStateOf("") }
    val brandSuggestions by viewModel.brandSuggestions.collectAsState()
    val companySuggestions by viewModel.companySuggestions.collectAsState()
    val isIos = currentPlatform == Platform.IOS

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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { saveOpen = true }) {
                        Text("Save to my list", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                    if (FeatureFlags.socialFeedEnabled) {
                        TextButton(onClick = { shareOpen = true }) {
                            Text("Share as story", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                        }
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
                            .clickable { onCandidateClick(candidate) },
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
    }

    if (saveOpen) {
        SaveToListBottomSheet(
            name = saveName,
            brand = saveBrand,
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
            onSave = {
                viewModel.saveToShelf(
                    displayName = saveName,
                    brand = saveBrand,
                    brandId = saveBrandId,
                    companyIds = selectedCompanies.map { it.id },
                )
                saveOpen = false
                saveName = ""
                saveBrand = ""
                saveBrandId = null
                selectedCompanies = emptyList()
            },
            onClose = { saveOpen = false },
            onOpen = {
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
    actionMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearActionMessage() },
            title = { Text("Done") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { viewModel.clearActionMessage() }) { Text("OK") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SaveToListBottomSheet(
    name: String,
    brand: String,
    brandSuggestions: List<CatalogBrandDto>,
    companySuggestions: List<CatalogCompanyDto>,
    selectedCompanies: List<CatalogCompanyDto>,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onBrandPick: (CatalogBrandDto) -> Unit,
    onCompanyQuery: (String) -> Unit,
    onCompanyToggle: (CatalogCompanyDto) -> Unit,
    onCompanyRemove: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
) {
    var showCompany by remember { mutableStateOf(selectedCompanies.isNotEmpty()) }
    var companyQuery by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { onOpen() }

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
                        "Link brand & company for later.",
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

            if (!showCompany) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OptionalAddChip("Company") {
                        showCompany = true
                        onCompanyQuery("")
                    }
                }
            } else {
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

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onSave,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
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
