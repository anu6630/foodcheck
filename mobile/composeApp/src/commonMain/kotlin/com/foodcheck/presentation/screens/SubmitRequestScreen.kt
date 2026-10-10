package com.foodcheck.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.presentation.state.SubmitRequestState
import com.foodcheck.presentation.state.SubmitRequestViewModel
import com.foodcheck.presentation.components.AttachedImageThumb
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform
import com.foodcheck.util.ImagePickerLauncher
import com.foodcheck.util.ImagePickerSource
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val COUNTRIES = listOf(
    "IN" to "India",
    "AU" to "Australia",
    "US" to "United States",
    "EU" to "European Union",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "SG" to "Singapore",
    "AE" to "UAE",
)

/** Global product taxonomy for submit requests (not country-specific). */
private val PRODUCT_CATEGORIES = listOf(
    "Beverage",
    "Bakery",
    "Confectionery",
    "Dairy",
    "Snack",
    "Ready meal",
    "Sauce & condiment",
    "Cosmetic",
    "Other",
)

/** Global ingredient taxonomy for submit requests (not country-specific). */
private val INGREDIENT_CATEGORIES = listOf(
    "additive",
    "colour",
    "preservative",
    "sweetener",
    "flavour",
    "emulsifier",
    "stabiliser",
    "acidulant",
    "antioxidant",
    "thickener",
    "other",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitRequestScreen(
    onBack: () -> Unit,
    imagePickerLauncher: ImagePickerLauncher? = null,
    viewModel: SubmitRequestViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val suggestions by viewModel.ingredientSuggestions.collectAsState()
    val isSearchingIngredients by viewModel.isSearchingIngredients.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Product form state
    var productName by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var productCategory by remember { mutableStateOf("") }
    var ingredientsText by remember { mutableStateOf("") }
    var productNotes by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(COUNTRIES[0]) }
    var countryMenuOpen by remember { mutableStateOf(false) }
    val productImages = remember { mutableStateListOf<ByteArray>() }

    // Ingredient form state
    var ingredientName by remember { mutableStateOf("") }
    var eNumber by remember { mutableStateOf("") }
    var ingredientCategory by remember { mutableStateOf("") }
    var ingredientNotes by remember { mutableStateOf("") }
    var selectedFromDb by remember { mutableStateOf(false) }
    var isConfirmedNew by remember { mutableStateOf(false) }

    // Reset state on tab change
    LaunchedEffect(selectedTab) { viewModel.resetState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .then(if (currentPlatform == Platform.IOS) Modifier.statusBarsPadding() else Modifier)
    ) {
        // ── Merged brand + screen header (global App topBar is hidden here) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        ) {
                            append("Life")
                        }
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = AppColors.Primary,
                            )
                        ) {
                            append("X")
                        }
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                fontSize = 20.sp,
                                color = Color.White,
                            )
                        ) {
                            append(" Food")
                        }
                    }
                )
                Text(
                    text = "Submit a Request",
                    fontSize = 12.sp,
                    color = AppColors.TextMuted,
                )
            }
        }

        // ── Tab Selector ─────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = AppColors.BackgroundSecondary,
            contentColor = AppColors.Primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Report Product", fontSize = 13.sp) },
                icon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Check Ingredient", fontSize = 13.sp) },
                icon = { Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        // ── Content ───────────────────────────────────────────────────────────
        when {
            state is SubmitRequestState.Success -> {
                val s = state as SubmitRequestState.Success
                SuccessBanner(requestId = s.requestId, onDone = { viewModel.resetState(); onBack() })
            }
            selectedTab == 0 -> ProductForm(
                productName = productName,
                brand = brand,
                barcode = barcode,
                category = productCategory,
                ingredientsText = ingredientsText,
                notes = productNotes,
                selectedCountry = selectedCountry,
                countryMenuOpen = countryMenuOpen,
                countries = COUNTRIES,
                images = productImages,
                suggestions = suggestions,
                isSearchingIngredients = isSearchingIngredients,
                isLoading = state is SubmitRequestState.Loading,
                errorMessage = (state as? SubmitRequestState.Error)?.message,
                imagePickerLauncher = imagePickerLauncher,
                onProductNameChange = { productName = it },
                onBrandChange = { brand = it },
                onBarcodeChange = { barcode = it },
                onCategoryChange = { productCategory = it },
                onIngredientsTextChange = {
                    ingredientsText = it
                    viewModel.searchIngredientRealtime(it)
                },
                onNotesChange = { productNotes = it },
                onCountrySelected = { selectedCountry = it; countryMenuOpen = false },
                onCountryMenuToggle = { countryMenuOpen = !countryMenuOpen },
                onRemoveImage = { productImages.removeAt(it) },
                onAddImage = { bytes -> if (productImages.size < 3) productImages.add(bytes) },
                onSubmit = {
                    viewModel.submitProduct(
                        productName = productName,
                        countryCode = selectedCountry.first,
                        brand = brand,
                        barcode = barcode,
                        category = productCategory,
                        ingredientsText = ingredientsText,
                        notes = productNotes,
                        images = productImages.toList(),
                    )
                }
            )
            else -> IngredientForm(
                ingredientName = ingredientName,
                eNumber = eNumber,
                category = ingredientCategory,
                notes = ingredientNotes,
                selectedFromDb = selectedFromDb,
                isConfirmedNew = isConfirmedNew,
                suggestions = suggestions,
                isSearchingIngredients = isSearchingIngredients,
                isLoading = state is SubmitRequestState.Loading,
                errorMessage = (state as? SubmitRequestState.Error)?.message,
                onIngredientNameChange = {
                    ingredientName = it
                    selectedFromDb = false
                    isConfirmedNew = false
                    viewModel.searchIngredientRealtime(it)
                },
                onENumberChange = { eNumber = it },
                onCategoryChange = { ingredientCategory = it },
                onNotesChange = { ingredientNotes = it },
                onSelectSuggestion = { ing ->
                    ingredientName = ing.name
                    eNumber = ing.eNumber ?: ""
                    ingredientCategory = ing.category ?: ""
                    selectedFromDb = true
                    isConfirmedNew = false
                    viewModel.clearSuggestions()
                },
                onConfirmNew = {
                    isConfirmedNew = true
                    viewModel.clearSuggestions()
                },
                onSubmit = {
                    viewModel.submitIngredient(
                        ingredientName = ingredientName,
                        eNumber = eNumber,
                        category = ingredientCategory,
                        notes = ingredientNotes,
                    )
                }
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Product Form — minimal core + optional chips
// ────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductForm(
    productName: String,
    brand: String,
    barcode: String,
    category: String,
    ingredientsText: String,
    notes: String,
    selectedCountry: Pair<String, String>,
    countryMenuOpen: Boolean,
    countries: List<Pair<String, String>>,
    images: List<ByteArray>,
    suggestions: List<AnnotatedIngredient>,
    isSearchingIngredients: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    imagePickerLauncher: ImagePickerLauncher?,
    onProductNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onIngredientsTextChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCountrySelected: (Pair<String, String>) -> Unit,
    onCountryMenuToggle: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onAddImage: (ByteArray) -> Unit,
    onSubmit: () -> Unit,
) {
    var showBrand by remember { mutableStateOf(false) }
    var showBarcode by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var showIngredients by remember { mutableStateOf(false) }
    var showPhotos by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("What's missing?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)

        FormField("Product name *", productName, onProductNameChange, placeholder = "e.g. KitKat Dark")
        CountryPicker(
            selectedCountry = selectedCountry,
            countryMenuOpen = countryMenuOpen,
            countries = countries,
            onCountrySelected = onCountrySelected,
            onCountryMenuToggle = onCountryMenuToggle,
        )

        if (showBrand) {
            DismissibleField(
                label = "Brand",
                onDismiss = { showBrand = false; onBrandChange("") },
            ) {
                FormField("", brand, onBrandChange, placeholder = "e.g. Nestle")
            }
        }
        if (showBarcode) {
            DismissibleField(
                label = "Barcode / EAN",
                onDismiss = { showBarcode = false; onBarcodeChange("") },
            ) {
                FormField("", barcode, onBarcodeChange, placeholder = "e.g. 8901058857781", keyboardType = KeyboardType.Number)
            }
        }
        if (showCategory) {
            DismissibleField(
                label = "Category",
                onDismiss = { showCategory = false; onCategoryChange("") },
            ) {
                CategoryChoiceRow(
                    options = PRODUCT_CATEGORIES,
                    selected = category,
                    onSelect = onCategoryChange,
                )
            }
        }
        if (showIngredients) {
            DismissibleField(
                label = "Ingredients",
                onDismiss = {
                    showIngredients = false
                    onIngredientsTextChange("")
                },
            ) {
                FormField(
                    label = "",
                    value = ingredientsText,
                    onValueChange = onIngredientsTextChange,
                    placeholder = "Paste ingredients from the label…",
                    minLines = 3,
                )
                if (ingredientsText.trim().length >= 2) {
                    IngredientMatchesPanel(
                        ingredientName = ingredientsText,
                        suggestions = suggestions,
                        isSearching = isSearchingIngredients,
                        onSelectSuggestion = null,
                        onConfirmNew = null,
                    )
                }
            }
        }
        if (showPhotos) {
            DismissibleField(
                label = "Photos (up to 3)",
                onDismiss = {
                    showPhotos = false
                    while (images.isNotEmpty()) onRemoveImage(0)
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.forEachIndexed { idx, bytes ->
                        AttachedImageThumb(
                            bytes = bytes,
                            onRemove = { onRemoveImage(idx) }
                        )
                    }
                }
                if (images.size < 3 && imagePickerLauncher != null) {
                    AddImageButton(onImagePicked = onAddImage, launcher = imagePickerLauncher)
                }
            }
        }
        if (showNotes) {
            DismissibleField(
                label = "Notes",
                onDismiss = { showNotes = false; onNotesChange("") },
            ) {
                FormField("", notes, onNotesChange, placeholder = "Anything for the reviewer", minLines = 2)
            }
        }

        val hiddenChips = buildList {
            if (!showBrand) add("Brand" to { showBrand = true })
            if (!showBarcode) add("Barcode" to { showBarcode = true })
            if (!showCategory) add("Category" to { showCategory = true })
            if (!showPhotos) add("Photo" to { showPhotos = true })
            if (!showIngredients) add("Ingredients" to { showIngredients = true })
            if (!showNotes) add("Notes" to { showNotes = true })
        }
        if (hiddenChips.isNotEmpty()) {
            Text("Add details (optional)", fontSize = 12.sp, color = AppColors.TextMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hiddenChips.forEach { (label, onClick) ->
                    OptionalChip(label = label, onClick = onClick)
                }
            }
        }

        errorMessage?.let {
            Text(it, color = AppColors.Red, fontSize = 13.sp)
        }

        SubmitButton(isLoading = isLoading, label = "Submit request", onClick = onSubmit)
        Text(
            "Queued for review — not published automatically.",
            fontSize = 11.sp,
            color = AppColors.TextMuted,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Ingredient Form — minimal core + optional chips
// ────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientForm(
    ingredientName: String,
    eNumber: String,
    category: String,
    notes: String,
    selectedFromDb: Boolean,
    isConfirmedNew: Boolean,
    suggestions: List<AnnotatedIngredient>,
    isSearchingIngredients: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onIngredientNameChange: (String) -> Unit,
    onENumberChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSelectSuggestion: (AnnotatedIngredient) -> Unit,
    onConfirmNew: () -> Unit,
    onSubmit: () -> Unit,
) {
    var showENumber by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

    // If a DB suggestion prefilled e-number/category, reveal those rows.
    LaunchedEffect(selectedFromDb, eNumber, category) {
        if (selectedFromDb) {
            if (eNumber.isNotBlank()) showENumber = true
            if (category.isNotBlank()) showCategory = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ingredient to check", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)

        FormField("Ingredient name *", ingredientName, onIngredientNameChange, placeholder = "e.g. Titanium dioxide")

        if (ingredientName.trim().length >= 2 && !selectedFromDb && !isConfirmedNew) {
            IngredientMatchesPanel(
                ingredientName = ingredientName,
                suggestions = suggestions,
                isSearching = isSearchingIngredients,
                onSelectSuggestion = onSelectSuggestion,
                onConfirmNew = onConfirmNew,
            )
        } else if (selectedFromDb) {
            CompactInfoBanner(
                icon = Icons.Default.CheckCircle,
                tint = AppColors.Primary,
                text = "Matched in FoodCheck — you can still ask us to review.",
            )
        } else if (isConfirmedNew) {
            CompactInfoBanner(
                icon = Icons.Default.Science,
                tint = AppColors.SecondaryLight,
                text = "Will be filed as a new ingredient request.",
            )
        }

        if (showENumber) {
            DismissibleField(
                label = "INS / E-number",
                onDismiss = { showENumber = false; onENumberChange("") },
            ) {
                FormField("", eNumber, onENumberChange, placeholder = "e.g. INS 171 or E171")
            }
        }
        if (showCategory) {
            DismissibleField(
                label = "Category",
                onDismiss = { showCategory = false; onCategoryChange("") },
            ) {
                CategoryChoiceRow(
                    options = INGREDIENT_CATEGORIES,
                    selected = category,
                    onSelect = onCategoryChange,
                )
            }
        }
        if (showNotes) {
            DismissibleField(
                label = "Notes",
                onDismiss = { showNotes = false; onNotesChange("") },
            ) {
                FormField(
                    "",
                    notes,
                    onNotesChange,
                    placeholder = "e.g. Is this banned in EU food?",
                    minLines = 2,
                )
            }
        }

        val hiddenChips = buildList {
            if (!showENumber) add("INS / E-number" to { showENumber = true })
            if (!showCategory) add("Category" to { showCategory = true })
            if (!showNotes) add("Notes" to { showNotes = true })
        }
        if (hiddenChips.isNotEmpty()) {
            Text("Add details (optional)", fontSize = 12.sp, color = AppColors.TextMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hiddenChips.forEach { (label, onClick) ->
                    OptionalChip(label = label, onClick = onClick)
                }
            }
        }

        errorMessage?.let {
            Text(it, color = AppColors.Red, fontSize = 13.sp)
        }

        SubmitButton(
            isLoading = isLoading,
            label = "Submit request",
            onClick = onSubmit,
        )
        Text(
            "We'll research bans & add it if valid.",
            fontSize = 11.sp,
            color = AppColors.TextMuted,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Realtime DB Matches Panel
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun IngredientMatchesPanel(
    ingredientName: String,
    suggestions: List<AnnotatedIngredient>,
    isSearching: Boolean,
    onSelectSuggestion: ((AnnotatedIngredient) -> Unit)?,
    onConfirmNew: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (suggestions.isNotEmpty()) AppColors.Primary.copy(alpha = 0.3f) else AppColors.Secondary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching database...", fontSize = 12.sp, color = AppColors.TextMuted)
                } else if (suggestions.isNotEmpty()) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Existing DB Matches (${suggestions.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                } else {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AppColors.SecondaryLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("No exact database match found", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.SecondaryLight)
                }
            }

            if (!isSearching && suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.take(3).forEach { ing ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (onSelectSuggestion != null) Modifier.clickable { onSelectSuggestion(ing) } else Modifier),
                            color = AppColors.BackgroundSecondary.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ing.name + (ing.eNumber?.let { " ($it)" } ?: ""),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    ing.category?.let { cat ->
                                        Text(cat, fontSize = 11.sp, color = AppColors.TextMuted)
                                    }
                                }

                                StatusBadge(severity = ing.severity)
                            }
                        }
                    }
                }
            }

            // Explicit Action Button to add as NEW ingredient
            if (!isSearching && onConfirmNew != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onConfirmNew),
                    color = AppColors.Primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add \"${ingredientName.trim()}\" as New Ingredient",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(severity: String) {
    val (label, bg, fg) = when (severity.lowercase()) {
        "red" -> Triple("Banned", AppColors.Red.copy(alpha = 0.2f), AppColors.Red)
        "amber" -> Triple("Restricted", AppColors.Amber.copy(alpha = 0.2f), AppColors.Amber)
        "green", "permitted" -> Triple("Permitted", AppColors.Green.copy(alpha = 0.2f), AppColors.Green)
        "gray" -> Triple("No data", AppColors.TextMuted.copy(alpha = 0.2f), AppColors.TextMuted)
        else -> Triple("No data", AppColors.TextMuted.copy(alpha = 0.2f), Color.White)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, fg.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun CountryPicker(
    selectedCountry: Pair<String, String>,
    countryMenuOpen: Boolean,
    countries: List<Pair<String, String>>,
    onCountrySelected: (Pair<String, String>) -> Unit,
    onCountryMenuToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Country *", fontSize = 13.sp, color = AppColors.TextMuted, fontWeight = FontWeight.Medium)
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCountryMenuToggle),
                color = AppColors.Surface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.4f)),
            ) {
                Text(
                    "${selectedCountry.second} (${selectedCountry.first})",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
            DropdownMenu(
                expanded = countryMenuOpen,
                onDismissRequest = onCountryMenuToggle,
                modifier = Modifier.background(AppColors.BackgroundSecondary),
            ) {
                countries.forEach { country ->
                    DropdownMenuItem(
                        text = { Text("${country.second} (${country.first})", color = Color.White) },
                        onClick = { onCountrySelected(country) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionalChip(label: String, onClick: () -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChoiceRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val normalizedSelected = when (selected.lowercase()) {
        "color" -> "colour"
        "flavor" -> "flavour"
        "stabilizer" -> "stabiliser"
        else -> selected
    }
    val displayOptions = if (
        normalizedSelected.isNotBlank() &&
        options.none { it.equals(normalizedSelected, ignoreCase = true) }
    ) {
        options + normalizedSelected
    } else {
        options
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        displayOptions.forEach { option ->
            val isSelected = normalizedSelected.equals(option, ignoreCase = true)
            Surface(
                modifier = Modifier.clickable {
                    // Tap again to clear selection
                    onSelect(if (isSelected) "" else option)
                },
                color = if (isSelected) AppColors.Primary.copy(alpha = 0.22f) else AppColors.Surface.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) AppColors.Primary else AppColors.Border.copy(alpha = 0.4f),
                ),
            ) {
                Text(
                    text = option.replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) AppColors.Primary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun DismissibleField(
    label: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 13.sp, color = AppColors.TextMuted, fontWeight = FontWeight.Medium)
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $label",
                tint = AppColors.TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDismiss),
            )
        }
        content()
    }
}

@Composable
private fun CompactInfoBanner(icon: ImageVector, tint: Color, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, fontSize = 12.sp, color = tint)
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotBlank()) {
            Text(label, fontSize = 13.sp, color = AppColors.TextMuted, fontWeight = FontWeight.Medium)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = AppColors.TextMuted.copy(alpha = 0.5f), fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            maxLines = if (minLines > 1) 8 else 1,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = keyboardType
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Primary,
                unfocusedBorderColor = AppColors.Border.copy(alpha = 0.4f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AppColors.Primary,
                focusedContainerColor = AppColors.Surface.copy(alpha = 0.3f),
                unfocusedContainerColor = AppColors.Surface.copy(alpha = 0.15f),
            )
        )
    }
}

@Composable
private fun AddImageButton(onImagePicked: (ByteArray) -> Unit, launcher: ImagePickerLauncher) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    val bytes = launcher.pickImage(ImagePickerSource.GALLERY)
                    bytes?.let { onImagePicked(it) }
                }
            },
        color = AppColors.Primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add photo", color = AppColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SubmitButton(isLoading: Boolean, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Primary,
            contentColor = Color.White,
            disabledContainerColor = AppColors.Primary.copy(alpha = 0.4f),
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SuccessBanner(requestId: Int, onDone: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(AppColors.Primary.copy(alpha = 0.3f), Color.Transparent))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(48.dp))
            }
            Text("Request Submitted!", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
            Text(
                "Your request #$requestId is now pending review. Our team will investigate and add it to the database.",
                color = AppColors.TextMuted,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
