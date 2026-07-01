package com.foodcheck.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.*
import com.foodcheck.presentation.state.MainViewModel
import com.foodcheck.util.readFileBytes
import org.koin.compose.koinInject
import com.foodcheck.data.datasources.NetworkDataSource

enum class Screen {
    SEARCH,
    DETAILS,
    SCAN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val viewModel: MainViewModel = koinInject()
    val networkDataSource: NetworkDataSource = koinInject()

    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    val selectedProduct by viewModel.selectedProduct.collectAsState()
    val detailsLoading by viewModel.detailsLoading.collectAsState()
    val detailsError by viewModel.detailsError.collectAsState()

    val scanResult by viewModel.scanResult.collectAsState()
    val scanLoading by viewModel.scanLoading.collectAsState()
    val scanError by viewModel.scanError.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.SEARCH) }
    var backendUrlInput by remember { mutableStateOf(networkDataSource.baseUrl) }
    var samplePathInput by remember { mutableStateOf("/Users/anuraj/.gemini/antigravity-ide/brain/4648c0c4-eaeb-4c1c-bc88-6b597bd771b0/real_cookie_label_1782911166414.png") }
    var showCountryDropdown by remember { mutableStateOf(false) }

    var selectedIngredientForDialog by remember { mutableStateOf<AnnotatedIngredient?>(null) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF10B981), // Emerald
            background = Color(0xFF0F172A), // Slate 900
            surface = Color(0xFF1E293B), // Slate 800
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF8FAFC),
            secondary = Color(0xFF6366F1) // Indigo
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "FoodCheck AI",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    },
                    actions = {
                        Box {
                            Button(
                                onClick = { showCountryDropdown = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Place, contentDescription = "Country", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(selectedCountry)
                            }
                            DropdownMenu(
                                expanded = showCountryDropdown,
                                onDismissRequest = { showCountryDropdown = false }
                            ) {
                                listOf("IN", "EU", "US").forEach { country ->
                                    DropdownMenuItem(
                                        text = { Text(country) },
                                        onClick = {
                                            viewModel.setCountry(country)
                                            showCountryDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.SEARCH || currentScreen == Screen.DETAILS,
                        onClick = {
                            viewModel.clearDetails()
                            currentScreen = Screen.SEARCH
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.SCAN,
                        onClick = {
                            viewModel.clearScan()
                            currentScreen = Screen.SCAN
                        },
                        icon = { Icon(Icons.Default.PhotoCamera, contentDescription = "Scan Label") },
                        label = { Text("AI Scan") }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .padding(16.dp)
            ) {
                when (currentScreen) {
                    Screen.SEARCH -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search Box
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.searchProducts(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search food products (e.g. Cookie, Bread)") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (searchLoading) {
                                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (searchError != null) {
                                Text(
                                    text = searchError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(searchResults) { result ->
                                        ProductCard(result) {
                                            viewModel.getProductDetails(result.product.barcode)
                                            currentScreen = Screen.DETAILS
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Screen.DETAILS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    viewModel.clearDetails()
                                    currentScreen = Screen.SEARCH
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                                Text("Product Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (detailsLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (detailsError != null) {
                                Text(text = detailsError ?: "", color = MaterialTheme.colorScheme.error)
                            } else if (selectedProduct != null) {
                                val details = selectedProduct!!
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = details.product.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 20.sp
                                                    )
                                                    SeverityBadge(details.severity)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Brand: ${details.product.brand ?: "Unknown"}",
                                                    color = Color.Gray
                                                )
                                                Text(
                                                    text = "Barcode: ${details.product.barcode}",
                                                    color = Color.Gray
                                                )
                                                Text(
                                                    text = "Category: ${details.product.category ?: "Unknown"}",
                                                    color = Color.Gray
                                                )
                                                Text(
                                                    text = "Data Source: ${details.product.source.uppercase()}",
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }

                                    item {
                                        Text(
                                            text = "Ingredients List (Tap to check bans/warnings):",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )
                                    }

                                    item {
                                        // Renders list of ingredients as colored chips
                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            details.ingredients.forEach { ingredient ->
                                                IngredientChip(ingredient) {
                                                    selectedIngredientForDialog = ingredient
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Screen.SCAN -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Text(
                                    text = "AI Vision Scanner Configuration",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            // Dynamic Server Configurations
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("FastAPI Backend URL:", fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = backendUrlInput,
                                            onValueChange = {
                                                backendUrlInput = it
                                                networkDataSource.baseUrl = it
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("http://localhost:8999 or live URL") }
                                        )
                                    }
                                }
                            }

                            // Vision test setup
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Vision Scanner Test File Path:", fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Points to the generated high-fidelity cookie label image.",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = samplePathInput,
                                            onValueChange = { samplePathInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                try {
                                                    val bytes = readFileBytes(samplePathInput)
                                                    viewModel.scanImage(bytes)
                                                } catch (e: Exception) {
                                                    viewModel.scanImage(ByteArray(0)) // Trigger validation error
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Scan Label from Local Path")
                                        }
                                    }
                                }
                            }

                            if (scanLoading) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("AI analyzing label image...", color = Color.Gray)
                                        }
                                    }
                                }
                            }

                            if (scanError != null) {
                                item {
                                    Text(
                                        text = scanError ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            // Display Scan Result Details
                            if (scanResult != null) {
                                val result = scanResult!!
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Extracted Intent: ${result.intent.uppercase()}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            val productData = result.data
                                            if (result.intent == "product" && productData != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Matched Product: ${productData.product.name} (${productData.product.brand ?: "Unknown"})")
                                                Text("Match Score: ${result.match_score ?: 0}%")
                                            }
                                        }
                                    }
                                }

                                if (result.ingredients.isNotEmpty()) {
                                    item {
                                        Text("Scanned Ingredients List (tap to inspect):", fontWeight = FontWeight.Bold)
                                    }
                                    item {
                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            result.ingredients.forEach { ingredient ->
                                                IngredientChip(ingredient) {
                                                    selectedIngredientForDialog = ingredient
                                                }
                                            }
                                        }
                                    }
                                }

                                if (result.matching_products.isNotEmpty()) {
                                    item {
                                        Text("Database Matching Products:", fontWeight = FontWeight.Bold)
                                    }
                                    items(result.matching_products) { match ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.getProductDetails(match.product.barcode)
                                                    currentScreen = Screen.DETAILS
                                                },
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(match.product.name, fontWeight = FontWeight.Bold)
                                                    SeverityBadge(match.severity)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Brand: ${match.product.brand ?: "Unknown"}", color = Color.Gray, fontSize = 12.sp)
                                                Text("Barcode: ${match.product.barcode}", color = Color.Gray, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ingredient inspection Dialog
        if (selectedIngredientForDialog != null) {
            val ing = selectedIngredientForDialog!!
            AlertDialog(
                onDismissRequest = { selectedIngredientForDialog = null },
                title = { Text(ing.name, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ing.eNumber != null) {
                            Text("E-Number: ${ing.eNumber}", fontWeight = FontWeight.SemiBold)
                        }
                        if (ing.category != null) {
                            Text("Category: ${ing.category}", color = Color.Gray)
                        }
                        Text("Analyzed Severity: ${ing.severity.uppercase()}")

                        val ban = ing.matchedBan
                        if (ban != null) {
                            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Regulatory Action (${ban.countryCode}):",
                                fontWeight = FontWeight.Bold,
                                color = getSeverityColor(ing.severity)
                            )
                            Text("Status: ${ban.status.uppercase()}", fontWeight = FontWeight.SemiBold)
                            if (ban.condition != null) {
                                Text("Condition: ${ban.condition}")
                            }
                            if (ban.regulationRef != null) {
                                Text("Reference: ${ban.regulationRef}", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                text = "Permitted or no restriction registered for selected country.",
                                color = Color.Gray
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedIngredientForDialog = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun ProductCard(result: SearchProductResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SeverityBadge(result.severity)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Brand: ${result.product.brand ?: "Unknown"}", color = Color.Gray, fontSize = 13.sp)
            Text(text = "Barcode: ${result.product.barcode}", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun SeverityBadge(severity: String) {
    val color = getSeverityColor(severity)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = severity.uppercase(),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun IngredientChip(ingredient: AnnotatedIngredient, onClick: () -> Unit) {
    val color = getSeverityColor(ingredient.severity)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = if (ingredient.eNumber != null) "${ingredient.name} (${ingredient.eNumber})" else ingredient.name
            Text(
                text = label,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun getSeverityColor(severity: String): Color {
    return when (severity.lowercase()) {
        "red" -> Color(0xFFEF4444)      // Crimson
        "amber" -> Color(0xFFF59E0B)    // Sunset Orange
        "green" -> Color(0xFF10B981)    // Emerald
        else -> Color(0xFF94A3B8)       // Slate Gray
    }
}
