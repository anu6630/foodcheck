package com.foodcheck.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform
import com.foodcheck.presentation.theme.PlatformBackground

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import com.foodcheck.util.ImagePickerSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.presentation.navigation.Route
import com.foodcheck.presentation.screens.IngredientDetailScreen
import com.foodcheck.presentation.screens.LandingScreen
import com.foodcheck.presentation.screens.ProductDetailScreen
import com.foodcheck.presentation.screens.ScanSuggestionsScreen
import com.foodcheck.presentation.screens.SearchResultsScreen
import com.foodcheck.presentation.screens.SubmitRequestScreen
import com.foodcheck.presentation.screens.FeedScreen
import com.foodcheck.presentation.screens.ShelfListScreen
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.util.ImagePickerLauncher
import org.koin.compose.viewmodel.koinViewModel

enum class ScreenTab { SCAN, SEARCH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(imagePickerLauncher: ImagePickerLauncher) {
    val navController = rememberNavController()
    val isIos = currentPlatform == Platform.IOS
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedTab by remember { mutableStateOf(ScreenTab.SEARCH) }
    var isSearching by remember { mutableStateOf(false) }
    var clearQueryTrigger by remember { mutableStateOf(0) }
    var showScanSheet by remember { mutableStateOf(false) }
    val scanViewModel: ScanViewModel = koinViewModel()

    var activeProductBarcode by remember { mutableStateOf<String?>(null) }
    var activeIngredientId by remember { mutableStateOf<String?>(null) }

    val onProductClick: (String) -> Unit = { barcode ->
        if (isIos) activeProductBarcode = barcode
        else navController.navigate(Route.ProductDetail(barcode))
    }
    val onIngredientClick: (String) -> Unit = { ingId ->
        if (isIos) activeIngredientId = ingId
        else navController.navigate(Route.IngredientDetail(ingId))
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Primary,
            background = AppColors.Background,
            surface = AppColors.Surface,
            onBackground = AppColors.OnBackground,
            onSurface = AppColors.OnBackground,
            secondary = AppColors.Secondary
        )
    ) {
        val isIos = currentPlatform == Platform.IOS
        // Screens that own a merged header (screen title + LifeX) — skip global brand bar.
        val hideGlobalTopBar = currentRoute?.let { route ->
            route.contains("SubmitRequest") || route.contains("IngredientDetail")
        } == true
        Scaffold(
            topBar = {
                if (!hideGlobalTopBar) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                        color = if (isIos) Color.White.copy(alpha = 0.03f) else AppColors.Surface.copy(alpha = 0.85f),
                        border = if (isIos) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)) else null
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            if (isIos) {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))) {
                                                append("Life")
                                            }
                                            withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 13.sp, color = AppColors.Primary)) {
                                                append("X")
                                            }
                                            withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = Color.White)) {
                                                append(" Food")
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Know what's banned, anywhere.",
                                        color = AppColors.TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isOnLanding = currentRoute == null || currentRoute.endsWith("Route.Landing")
                                    // Screens that already render their own back control — avoid a second
                                    // back arrow next to the LifeX brand header.
                                    val hasOwnBack = currentRoute?.let { route ->
                                        route.contains("ProductDetail") ||
                                            route.contains("ScanSuggestions")
                                    } == true
                                    val canNavigateBack =
                                        (!isOnLanding && !hasOwnBack) ||
                                            (selectedTab == ScreenTab.SEARCH && isSearching)
                                    if (canNavigateBack) {
                                        IconButton(
                                            onClick = {
                                                if (isOnLanding) {
                                                    clearQueryTrigger++
                                                } else {
                                                    navController.popBackStack()
                                                }
                                            },
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = Color.White
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(AppColors.Primary, shape = RoundedCornerShape(4.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(verticalArrangement = Arrangement.Center) {
                                        Text(
                                            text = buildAnnotatedString {
                                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))) {
                                                    append("Life")
                                                }
                                                withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 15.sp, color = AppColors.Primary)) {
                                                    append("X")
                                                }
                                                withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = Color.White)) {
                                                    append(" Food")
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Know what's banned, anywhere.",
                                            color = AppColors.TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            PlatformBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                NavHost(navController = navController, startDestination = Route.Landing) {
                    composable<Route.Landing> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            SearchResultsScreen(
                                initialQuery = "",
                                onQueryChange = { isSearching = it },
                                clearQueryTrigger = clearQueryTrigger,
                                onProductClick = { result -> onProductClick(result.product.barcode) },
                                onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } },
                                onMyListClick = { navController.navigate(Route.MyList) }
                            )

                            // Floating Bottom Tab Bar overlay
                            AnimatedVisibility(
                                visible = !isSearching,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .padding(bottom = 24.dp)
                                        .shadow(12.dp, shape = RoundedCornerShape(24.dp)),
                                    color = Color(0xFF0F1123).copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TabButtonCompose(
                                            icon = Icons.Default.Search,
                                            title = "Search",
                                            isActive = true,
                                            onClick = { /* Already on Search */ }
                                        )
                                        TabButtonCompose(
                                            icon = Icons.Default.DocumentScanner,
                                            title = "Scan",
                                            isActive = false,
                                            onClick = { showScanSheet = true }
                                        )
                                        TabButtonCompose(
                                            icon = Icons.Default.Forum,
                                            title = "Feed",
                                            isActive = false,
                                            onClick = { navController.navigate(Route.Feed) }
                                        )
                                        TabButtonCompose(
                                            icon = Icons.Default.Add,
                                            title = "Submit",
                                            isActive = false,
                                            onClick = { navController.navigate(Route.SubmitRequest) }
                                        )
                                    }
                                }
                            }
                        }

                        // Android Scan Bottom Sheet overlay
                        if (showScanSheet) {
                            ScanBottomSheet(
                                onDismiss = { showScanSheet = false },
                                imagePickerLauncher = imagePickerLauncher,
                                viewModel = scanViewModel,
                                onScanOutcome = { _ ->
                                    navController.navigate(Route.ScanSuggestions)
                                }
                            )
                        }
                    }
                    composable<Route.ScanSuggestions> {
                        ScanSuggestionsScreen(
                            viewModel = scanViewModel,
                            onCandidateClick = { candidate ->
                                navController.navigate(Route.ProductDetail(candidate.product.barcode)) {
                                    popUpTo(Route.Landing)
                                }
                            },
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } }
                        )
                    }
                    composable<Route.Feed> {
                        FeedScreen(
                            onBack = { navController.popBackStack() },
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } }
                        )
                    }
                    composable<Route.MyList> {
                        ShelfListScreen(
                            onBack = { navController.popBackStack() },
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } }
                        )
                    }
                    composable<Route.ProductDetail> { entry ->
                        val route = entry.toRoute<Route.ProductDetail>()
                        ProductDetailScreen(
                            barcode = route.barcode,
                            onBack = { navController.popBackStack() },
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } }
                        )
                    }
                    composable<Route.IngredientDetail> { entry ->
                        val route = entry.toRoute<Route.IngredientDetail>()
                        IngredientDetailScreen(
                            ingredientId = route.ingredientId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<Route.SubmitRequest> {
                        SubmitRequestScreen(
                            onBack = { navController.popBackStack() },
                            imagePickerLauncher = imagePickerLauncher,
                        )
                    }
                }

                // Modal Bottom Sheets for iOS Details presentation
                if (isIos) {
                    activeProductBarcode?.let { barcode ->
                        ModalBottomSheet(
                            onDismissRequest = { activeProductBarcode = null },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = AppColors.BackgroundSecondary,
                            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
                        ) {
                            ProductDetailScreen(
                                barcode = barcode,
                                onBack = { activeProductBarcode = null },
                                onIngredientClick = { ing ->
                                    activeProductBarcode = null
                                    ing.id?.let { activeIngredientId = it }
                                }
                            )
                        }
                    }

                    activeIngredientId?.let { ingredientId ->
                        ModalBottomSheet(
                            onDismissRequest = { activeIngredientId = null },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = AppColors.BackgroundSecondary,
                            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
                        ) {
                            IngredientDetailScreen(
                                ingredientId = ingredientId,
                                onBack = { activeIngredientId = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabButtonCompose(
    icon: ImageVector,
    title: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .width(60.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isActive) AppColors.Primary else Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .size(24.dp)
                .scale(if (isActive) 1.1f else 1.0f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBottomSheet(
    onDismiss: () -> Unit,
    imagePickerLauncher: ImagePickerLauncher,
    viewModel: ScanViewModel,
    onScanOutcome: (ScanOutcome) -> Unit
) {
    val loading by viewModel.loading.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val error by viewModel.error.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    val scope = rememberCoroutineScope()
    
    var pickedImages by remember { mutableStateOf(listOf<ByteArray>()) }

    LaunchedEffect(outcome) {
        outcome?.let {
            onScanOutcome(it)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.BackgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Scan Ingredients",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Attach up to 3 photos of the label or front pack",
                fontSize = 12.sp,
                color = AppColors.TextMuted
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (loading) {
                CircularProgressIndicator(color = AppColors.Primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(statusText, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                if (error != null) {
                    Text(error ?: "", color = Color.Red, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Picked Images Previews
                if (pickedImages.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        pickedImages.forEachIndexed { index, bytes ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AppColors.Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Photo ${index + 1}",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clickable { pickedImages = pickedImages.filterIndexed { i, _ -> i != index } }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Remove",
                                        tint = Color.Red.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (pickedImages.size < 3) {
                                scope.launch {
                                    val image = imagePickerLauncher.pickImage(ImagePickerSource.CAMERA)
                                    if (image != null) {
                                        pickedImages = pickedImages + image
                                    }
                                }
                            }
                        },
                        enabled = pickedImages.size < 3,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = AppColors.Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Camera", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (pickedImages.size < 3) {
                                scope.launch {
                                    val image = imagePickerLauncher.pickImage(ImagePickerSource.GALLERY)
                                    if (image != null) {
                                        pickedImages = pickedImages + image
                                    }
                                }
                            }
                        },
                        enabled = pickedImages.size < 3,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gallery", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (pickedImages.isNotEmpty()) {
                            viewModel.scan(pickedImages)
                        }
                    },
                    enabled = pickedImages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Start Scan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
