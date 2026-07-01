package com.foodcheck.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    // Curated dark gradient colors
    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF020617), Color(0xFF0F172A)) // Slate 950 -> Slate 900
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF10B981), // Emerald 500
            background = Color(0xFF020617),
            surface = Color(0xFF1E293B),
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF8FAFC),
            secondary = Color(0xFF6366F1) // Indigo 500
        )
    ) {
        Scaffold(
            topBar = {
                // Glassmorphic Custom Top Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1E293B).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFF475569).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF10B981), shape = RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FoodCheck AI",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                letterSpacing = 0.5.sp,
                                color = Color.White
                            )
                        }

                        // Country Selector
                        Box {
                            Surface(
                                modifier = Modifier
                                    .clickable { showCountryDropdown = true }
                                    .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                color = Color(0xFF6366F1).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = "Country",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedCountry,
                                        color = Color(0xFFE0E7FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showCountryDropdown,
                                onDismissRequest = { showCountryDropdown = false }
                            ) {
                                listOf("IN", "EU", "US").forEach { country ->
                                    DropdownMenuItem(
                                        text = { Text(country, fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            viewModel.setCountry(country)
                                            showCountryDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.9f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(1.dp, Color(0xFF475569).copy(alpha = 0.2f), RoundedCornerShape(0.dp))
                ) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.SEARCH || currentScreen == Screen.DETAILS,
                        onClick = {
                            viewModel.clearDetails()
                            currentScreen = Screen.SEARCH
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search", fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0xFF10B981).copy(alpha = 0.1f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.SCAN,
                        onClick = {
                            viewModel.clearScan()
                            currentScreen = Screen.SCAN
                        },
                        icon = { Icon(Icons.Default.DocumentScanner, contentDescription = "Scan Label") },
                        label = { Text("AI Scan", fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0xFF10B981).copy(alpha = 0.1f)
                        )
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgGradient)
                    .padding(padding)
            ) {
                when (currentScreen) {
                    Screen.SEARCH -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            // Modern Search Bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.searchProducts(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        Color(0xFF10B981).copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    ),
                                placeholder = { Text("Search products by name or brand...", color = Color(0xFF64748B)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.searchProducts("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.6f),
                                    unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.4f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            if (searchLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF10B981))
                                }
                            } else if (searchError != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        color = Color(0xFFF43F5E).copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF43F5E))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text = searchError ?: "", color = Color(0xFFFDA4AF))
                                        }
                                    }
                                }
                            } else if (searchQuery.isBlank() && searchResults.isEmpty()) {
                                // Beautiful Empty State
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFF6366F1).copy(alpha = 0.6f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Audit Your Ingredients",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Search for a product below or try scanning a nutritional label in the AI Scan tab.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = "Popular Searches:",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Cookie", "Bread", "Frosting").forEach { item ->
                                            Surface(
                                                modifier = Modifier
                                                    .clickable { viewModel.searchProducts(item) }
                                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(20.dp)),
                                                color = Color(0xFF1E293B),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Text(
                                                    text = item,
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            // Header navigation row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.clearDetails()
                                        currentScreen = Screen.SEARCH
                                    },
                                    modifier = Modifier
                                        .background(Color(0xFF1E293B), shape = RoundedCornerShape(12.dp))
                                        .size(38.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Product Inspection",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (detailsLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF10B981))
                                }
                            } else if (detailsError != null) {
                                Text(text = detailsError ?: "", color = MaterialTheme.colorScheme.error)
                            } else if (selectedProduct != null) {
                                val details = selectedProduct!!
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    // Main Product Info Card
                                    item {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, Color(0xFF475569).copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(20.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = details.product.name,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 22.sp,
                                                            color = Color.White
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "Brand: ${details.product.brand ?: "Unknown"}",
                                                            color = Color(0xFF10B981),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    SeverityBadge(details.severity)
                                                }
                                                Divider(color = Color(0xFF475569).copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 14.dp))
                                                
                                                Row(modifier = Modifier.fillMaxWidth()) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("BARCODE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Text(details.product.barcode, color = Color(0xFFCBD5E1), fontSize = 13.sp)
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("SOURCE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Text(details.product.source.uppercase(), color = Color(0xFFCBD5E1), fontSize = 13.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Render Ingredients grouped by severity
                                    val banned = details.ingredients.filter { it.severity.lowercase() == "red" }
                                    val restricted = details.ingredients.filter { it.severity.lowercase() == "amber" }
                                    val safeAndUnknown = details.ingredients.filter { it.severity.lowercase() != "red" && it.severity.lowercase() != "amber" }

                                    if (banned.isNotEmpty()) {
                                        item {
                                            IngredientSectionHeader("🚨 Banned Additives (${banned.size})", Color(0xFFF43F5E))
                                        }
                                        item {
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                banned.forEach { ingredient ->
                                                    IngredientChip(ingredient) { selectedIngredientForDialog = ingredient }
                                                }
                                            }
                                        }
                                    }

                                    if (restricted.isNotEmpty()) {
                                        item {
                                            IngredientSectionHeader("⚠️ Restricted/Warning Additives (${restricted.size})", Color(0xFFF59E0B))
                                        }
                                        item {
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                restricted.forEach { ingredient ->
                                                    IngredientChip(ingredient) { selectedIngredientForDialog = ingredient }
                                                }
                                            }
                                        }
                                    }

                                    if (safeAndUnknown.isNotEmpty()) {
                                        item {
                                            IngredientSectionHeader("🔍 General Ingredients & Safe Additives", Color(0xFF94A3B8))
                                        }
                                        item {
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                safeAndUnknown.forEach { ingredient ->
                                                    IngredientChip(ingredient) { selectedIngredientForDialog = ingredient }
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Text(
                                    text = "AI Vision Label Scanner",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            }

                            // Glowing camera simulation viewport with laser animation
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .border(2.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Infinite laser transition
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val laserOffset by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(durationMillis = 2200, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        )
                                    )

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val y = size.height * laserOffset
                                        drawLine(
                                            brush = Brush.horizontalGradient(
                                                listOf(Color.Transparent, Color(0xFF10B981), Color.Transparent)
                                            ),
                                            start = androidx.compose.ui.geometry.Offset(0f, y),
                                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                                            strokeWidth = 3.dp.toPx()
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.CenterFocusStrong,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981).copy(alpha = 0.7f),
                                            modifier = Modifier.size(42.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "OCR CAMERA RUNNING",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 2.sp,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }

                            // Config Fields Cards
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF475569).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                    color = Color(0xFF1E293B).copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Backend Server Endpoints", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = backendUrlInput,
                                            onValueChange = {
                                                backendUrlInput = it
                                                networkDataSource.baseUrl = it
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF475569).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                    color = Color(0xFF1E293B).copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Test Label Image Path", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = samplePathInput,
                                            onValueChange = { samplePathInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 2,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = {
                                                try {
                                                    val bytes = readFileBytes(samplePathInput)
                                                    viewModel.scanImage(bytes)
                                                } catch (e: Exception) {
                                                    viewModel.scanImage(ByteArray(0))
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Scan Label Image File", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (scanLoading) {
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF10B981).copy(alpha = 0.05f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(color = Color(0xFF10B981))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("AI Vision OCR parsing label ingredients...", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            if (scanError != null) {
                                item {
                                    Surface(
                                        color = Color(0xFFF43F5E).copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF43F5E))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text = scanError ?: "", color = Color(0xFFFDA4AF))
                                        }
                                    }
                                }
                            }

                            if (scanResult != null) {
                                val result = scanResult!!
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                        color = Color(0xFF6366F1).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Extracted Intent: ${result.intent.uppercase()}",
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF818CF8),
                                                fontSize = 14.sp
                                            )
                                            val productData = result.data
                                            if (result.intent == "product" && productData != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "Matched Product: ${productData.product.name}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text("Brand: ${productData.product.brand ?: "Unknown"}", color = Color.Gray)
                                                Text("Match Confidence: ${result.match_score ?: 0}%", color = Color(0xFF10B981))
                                            }
                                        }
                                    }
                                }

                                val scannedBanned = result.ingredients.filter { it.severity.lowercase() == "red" }
                                val scannedRestricted = result.ingredients.filter { it.severity.lowercase() == "amber" }
                                val scannedSafe = result.ingredients.filter { it.severity.lowercase() != "red" && it.severity.lowercase() != "amber" }

                                if (scannedBanned.isNotEmpty()) {
                                    item {
                                        IngredientSectionHeader("🚨 Detected Banned Additives (${scannedBanned.size})", Color(0xFFF43F5E))
                                    }
                                    item {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            scannedBanned.forEach { ingredient ->
                                                IngredientChip(ingredient) { selectedIngredientForDialog = ingredient }
                                            }
                                        }
                                    }
                                }

                                if (scannedRestricted.isNotEmpty()) {
                                    item {
                                        IngredientSectionHeader("⚠️ Detected Restricted Additives (${scannedRestricted.size})", Color(0xFFF59E0B))
                                    }
                                    item {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            scannedRestricted.forEach { ingredient ->
                                                IngredientChip(ingredient) { selectedIngredientForDialog = ingredient }
                                            }
                                        }
                                    }
                                }

                                if (scannedSafe.isNotEmpty()) {
                                    item {
                                        IngredientSectionHeader("🔍 Other Scanned Ingredients", Color(0xFF94A3B8))
                                    }
                                    item {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            scannedSafe.forEach { ingredient ->
                                                IngredientChip(ingredient) { selectedIngredientForDialog = ingredient }
                                            }
                                        }
                                    }
                                }

                                if (result.matching_products.isNotEmpty()) {
                                    item {
                                        Text("Database Match Recommendations:", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    items(result.matching_products) { match ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, Color(0xFF475569).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                                .clickable {
                                                    viewModel.getProductDetails(match.product.barcode)
                                                    currentScreen = Screen.DETAILS
                                                },
                                            color = Color(0xFF1E293B).copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(match.product.name, fontWeight = FontWeight.Bold, color = Color.White)
                                                    Text("Brand: ${match.product.brand ?: "Unknown"}", color = Color.Gray, fontSize = 12.sp)
                                                }
                                                SeverityBadge(match.severity)
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

        // Beautiful custom dialog for details check
        if (selectedIngredientForDialog != null) {
            val ing = selectedIngredientForDialog!!
            AlertDialog(
                onDismissRequest = { selectedIngredientForDialog = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val color = getSeverityColor(ing.severity)
                        Box(modifier = Modifier.size(10.dp).background(color, shape = RoundedCornerShape(5.dp)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ing.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (ing.eNumber != null) {
                            Text(
                                text = "Regulatory Code: ${ing.eNumber}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF818CF8)
                            )
                        }
                        if (ing.category != null) {
                            Text(
                                text = "Category: ${ing.category?.uppercase() ?: ""}",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        val ban = ing.matchedBan
                        if (ban != null) {
                            Divider(color = Color(0xFF475569).copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                            Surface(
                                color = getSeverityColor(ing.severity).copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, getSeverityColor(ing.severity).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Country Regulation (${ban.countryCode})",
                                        fontWeight = FontWeight.Bold,
                                        color = getSeverityColor(ing.severity),
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Status: ${ban.status.uppercase()}",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    if (ban.condition != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "Condition: ${ban.condition}", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                                    }
                                    if (ban.regulationRef != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Ref: ${ban.regulationRef}",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "No bans or warning restrictions are active for this ingredient in $selectedCountry.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedIngredientForDialog = null }) {
                        Text("Dismiss", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
fun IngredientSectionHeader(title: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 16.dp)
                .background(color, shape = RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            fontSize = 15.sp
        )
    }
}

@Composable
fun ProductCard(result: SearchProductResult, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF475569).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .animateContentSize(),
        color = Color(0xFF1E293B).copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SeverityBadge(result.severity)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Brand: ${result.product.brand ?: "Unknown"}",
                color = Color(0xFF10B981),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Text(
                text = "Barcode: ${result.product.barcode}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SeverityBadge(severity: String) {
    val color = getSeverityColor(severity)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = severity.uppercase(),
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun IngredientChip(ingredient: AnnotatedIngredient, onClick: () -> Unit) {
    val color = getSeverityColor(ingredient.severity)
    val icon = when (ingredient.severity.lowercase()) {
        "red" -> Icons.Default.Cancel
        "amber" -> Icons.Default.Warning
        "green" -> Icons.Default.CheckCircle
        else -> Icons.Default.Help
    }
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            val label = if (ingredient.eNumber != null) "${ingredient.name} (${ingredient.eNumber})" else ingredient.name
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun getSeverityColor(severity: String): Color {
    return when (severity.lowercase()) {
        "red" -> Color(0xFFF43F5E)      // Rose red
        "amber" -> Color(0xFFF59E0B)    // Amber orange
        "green" -> Color(0xFF34D399)    // Emerald green
        else -> Color(0xFF94A3B8)       // Slate gray
    }
}
