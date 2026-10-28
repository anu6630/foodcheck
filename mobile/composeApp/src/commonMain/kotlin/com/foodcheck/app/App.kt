package com.foodcheck.app

import com.foodcheck.presentation.state.CompareSlot
import com.foodcheck.presentation.navigation.HandleBack
import com.foodcheck.ads.AdsController
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
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
import com.foodcheck.presentation.theme.platformStatusBarsPadding

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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import com.foodcheck.presentation.components.AttachedImageThumb
import com.foodcheck.presentation.components.UserAvatar
import androidx.compose.material.icons.filled.Bookmark
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
import com.foodcheck.presentation.screens.CompareScreen
import com.foodcheck.presentation.screens.CompareResultScreen
import com.foodcheck.presentation.screens.ComparisonDetailScreen
import com.foodcheck.presentation.state.CompareViewModel
import androidx.compose.material.icons.filled.Balance
import com.foodcheck.presentation.state.FeedViewModel
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.components.MonthlyScansUsedDialog
import com.foodcheck.FeatureFlags
import com.foodcheck.ads.BannerAd
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
    val compareViewModel: CompareViewModel = koinViewModel()
    val feedViewModel: FeedViewModel = koinViewModel()
    val authViewModel: com.foodcheck.presentation.state.AuthViewModel = koinViewModel()
    var showAuthSheet by remember { mutableStateOf(false) }
    val authState by authViewModel.state.collectAsState()
    val selectedStory by feedViewModel.selected.collectAsState()

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
        // Screens that own their chrome — skip global LifeX brand bar.
        val hideGlobalTopBar = currentRoute?.let { route ->
            route.contains("SubmitRequest") ||
                route.contains("IngredientDetail") ||
                route.contains("ScanSuggestions")
        } == true
        Scaffold(
            topBar = {
                if (!hideGlobalTopBar) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isIos) Color.White.copy(alpha = 0.03f) else AppColors.Surface,
                        border = if (isIos) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)) else null
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .platformStatusBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            val isOnLanding = currentRoute == null || currentRoute.endsWith("Route.Landing")
                            val isOnFeed = currentRoute?.contains("Feed") == true
                            val isOnMyList = currentRoute?.contains("MyList") == true
                            val topSubtitle = when {
                                isOnFeed && selectedStory != null -> "Story"
                                isOnFeed -> "Community"
                                isOnMyList -> "My list · private"
                                currentRoute?.contains("ComparisonDetail") == true -> "Saved comparison"
                                currentRoute?.contains("Compare") == true -> "Compare products"
                                else -> "Know what's banned, anywhere."
                            }
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
                                    Text(topSubtitle, color = AppColors.TextMuted, fontSize = 10.sp)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Screens that already render their own back control — avoid a second
                                    // back arrow next to the LifeX brand header.
                                    val hasOwnBack = currentRoute?.let { route ->
                                        route.contains("ProductDetail") ||
                                            route.contains("IngredientDetail") ||
                                            route.contains("SubmitRequest")
                                    } == true
                                    val canNavigateBack =
                                        selectedStory != null ||
                                            (!isOnLanding && !hasOwnBack) ||
                                            (selectedTab == ScreenTab.SEARCH && isSearching)
                                    if (canNavigateBack) {
                                        IconButton(
                                            onClick = {
                                                when {
                                                    // Story detail is in-memory state, not a nav destination.
                                                    selectedStory != null -> feedViewModel.close()
                                                    isOnLanding -> clearQueryTrigger++
                                                    else -> navController.popBackStack()
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
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
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
                                        Text(topSubtitle, color = AppColors.TextMuted, fontSize = 11.sp)
                                    }
                                    IconButton(
                                        onClick = { showAuthSheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        val profile = authState.profile
                                        if (profile != null && !profile.isAnonymous) {
                                            UserAvatar(
                                                photoUrl = profile.photoUrl,
                                                displayName = profile.displayName,
                                                size = 28.dp,
                                                borderColor = AppColors.Green,
                                                borderWidth = 1.2.dp
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.AccountCircle,
                                                    contentDescription = "Sign In / Sync",
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
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
                        // Search fills remaining space; bottom chrome is a real column so the
                        // banner never overlays the tab bar (AdView z-order quirk).
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                SearchResultsScreen(
                                    initialQuery = "",
                                    onQueryChange = { isSearching = it },
                                    clearQueryTrigger = clearQueryTrigger,
                                    onProductClick = { result -> onProductClick(result.product.barcode) },
                                    onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } },
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppColors.Background)
                                    .navigationBarsPadding(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Banner above the tab menu (clipped AdView — see BannerAd.android).
                                BannerAd(modifier = Modifier.fillMaxWidth())
                                AnimatedVisibility(
                                    visible = !isSearching,
                                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .padding(top = 10.dp, bottom = 16.dp)
                                            .shadow(2.dp, shape = RoundedCornerShape(24.dp)),
                                        color = Color(0xFF0F1123).copy(alpha = 0.96f),
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
                                                icon = Icons.Default.Balance,
                                                title = "Compare",
                                                isActive = false,
                                                onClick = { navController.navigate(Route.Compare) }
                                            )
                                            if (FeatureFlags.socialFeedEnabled) {
                                                TabButtonCompose(
                                                    icon = Icons.Default.Forum,
                                                    title = "Feed",
                                                    isActive = false,
                                                    onClick = { navController.navigate(Route.Feed) }
                                                )
                                            }
                                            TabButtonCompose(
                                                icon = Icons.Default.Bookmark,
                                                title = "My list",
                                                isActive = false,
                                                onClick = { navController.navigate(Route.MyList) }
                                            )
                                        }
                                    }
                                }
                                if (isSearching) {
                                    Spacer(modifier = Modifier.height(12.dp))
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
                        val compareState by compareViewModel.state.collectAsState()
                        ScanSuggestionsScreen(
                            viewModel = scanViewModel,
                            onBack = {
                                scanViewModel.clear()
                                navController.popBackStack()
                            },
                            onCandidateClick = { candidate ->
                                scanViewModel.clear()
                                navController.navigate(Route.ProductDetail(candidate.product.barcode)) {
                                    popUpTo(Route.Landing)
                                }
                            },
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } },
                            compareSlots = compareState.slots.map { slot ->
                                when (slot) {
                                    CompareSlot.Empty -> null
                                    is CompareSlot.Ready -> slot.name
                                    is CompareSlot.Loading -> slot.name
                                    else -> "Product"
                                }
                            },
                            onAddToCompare = { replaceSlot ->
                                scanViewModel.outcome.value?.let { outcome ->
                                    compareViewModel.addFromScan(scanViewModel.capturedImages(), outcome, replaceSlot)
                                }
                                navController.navigate(Route.Compare)
                            }
                        )
                    }
                    composable<Route.Compare> {
                        CompareScreen(
                            viewModel = compareViewModel,
                            imagePickerLauncher = imagePickerLauncher,
                            onCompare = { navController.navigate(Route.CompareResult) },
                            onOpenComparison = { id -> navController.navigate(Route.ComparisonDetail(id)) },
                        )
                    }
                    composable<Route.CompareResult> {
                        CompareResultScreen(
                            viewModel = compareViewModel,
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } },
                            onSaved = { id, first ->
                                val open = {
                                    navController.navigate(Route.ComparisonDetail(id)) {
                                        popUpTo(Route.Landing)
                                    }
                                }
                                // Task finished: a natural break for an ad — except the first time, so a
                                // new user sees the feature work end to end once.
                                if (first) open() else AdsController.showInterstitial { open() }
                            },
                        )
                    }
                    composable<Route.ComparisonDetail> { entry ->
                        val route = entry.toRoute<Route.ComparisonDetail>()
                        ComparisonDetailScreen(
                            id = route.id,
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } },
                            onRedo = { navController.navigate(Route.Compare) },
                            onDeleted = { navController.popBackStack() },
                            compareViewModel = compareViewModel,
                        )
                    }
                    if (FeatureFlags.socialFeedEnabled) {
                        composable<Route.Feed> {
                            FeedScreen(
                                viewModel = feedViewModel,
                                onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } }
                            )
                        }
                    }
                    composable<Route.MyList> {
                        ShelfListScreen(
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } },
                            onOpenComparison = { id -> navController.navigate(Route.ComparisonDetail(id)) },
                            onCompareItem = { item ->
                                compareViewModel.startFromShelf(item)
                                navController.navigate(Route.Compare)
                            },
                        )
                    }
                    composable<Route.ProductDetail> { entry ->
                        val route = entry.toRoute<Route.ProductDetail>()
                        // Leaving a product page is a break (she has read it); the tap that opened it was not.
                        val leave = { AdsController.showInterstitial { navController.popBackStack() } }
                        HandleBack(enabled = true, onBack = leave)
                        ProductDetailScreen(
                            barcode = route.barcode,
                            onBack = leave,
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

        if (showAuthSheet) {
            com.foodcheck.presentation.components.AuthSheet(
                viewModel = authViewModel,
                onDismiss = { showAuthSheet = false }
            )
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
    val quota by viewModel.quota.collectAsState()
    val quotaExceeded by viewModel.quotaExceeded.collectAsState()
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
            Spacer(modifier = Modifier.height(12.dp))

            quota?.let { q ->
                Surface(
                    onClick = { viewModel.showQuotaDialog() },
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        1.dp,
                        if (q.isExceeded) Color(0xFFEF4444).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (q.isExceeded) Color(0xFFEF4444) else AppColors.Primary,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${q.remaining} of ${q.limit} AI scans left this month",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

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

                if (pickedImages.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        pickedImages.forEachIndexed { index, bytes ->
                            AttachedImageThumb(
                                bytes = bytes,
                                onRemove = {
                                    pickedImages = pickedImages.filterIndexed { i, _ -> i != index }
                                }
                            )
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
                                com.foodcheck.analytics.getAnalyticsTracker().logScanInputSelected("camera", "scan_bottom_sheet")
                                scope.launch {
                                    val image = imagePickerLauncher.pickImage(ImagePickerSource.CAMERA)
                                    com.foodcheck.analytics.getAnalyticsTracker().logImagePickerResult("camera", image != null, image?.size)
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
                                com.foodcheck.analytics.getAnalyticsTracker().logScanInputSelected("gallery", "scan_bottom_sheet")
                                scope.launch {
                                    val image = imagePickerLauncher.pickImage(ImagePickerSource.GALLERY)
                                    com.foodcheck.analytics.getAnalyticsTracker().logImagePickerResult("gallery", image != null, image?.size)
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
                            viewModel.scan(pickedImages, source = "bottom_sheet")
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

    val showPaywall by viewModel.showPaywall.collectAsState()
    val billingManager = remember { com.foodcheck.billing.getBillingManager() }
    val proDetails by billingManager.proPlanDetails.collectAsState()
    val purchaseState by billingManager.purchaseState.collectAsState()

    LaunchedEffect(purchaseState) {
        when (val state = purchaseState) {
            is com.foodcheck.billing.BillingPurchaseState.Success -> {
                viewModel.onPurchaseSuccessful(state.purchaseToken, state.productId)
                billingManager.resetPurchaseState()
            }
            else -> {}
        }
    }

    quotaExceeded?.let { q ->
        MonthlyScansUsedDialog(
            quota = q,
            onDismiss = { viewModel.dismissQuotaExceeded("app_global_dialog") },
            onUpgradeClick = {
                com.foodcheck.analytics.getAnalyticsTracker().logQuotaDialogUpgradeClicked("app_global_dialog")
                viewModel.openPaywall("app_global_dialog")
            }
        )
    }

    if (showPaywall) {
        val isLoading = purchaseState is com.foodcheck.billing.BillingPurchaseState.InProgress
        val errorMsg = (purchaseState as? com.foodcheck.billing.BillingPurchaseState.Error)?.message
        val price = proDetails?.formattedPrice ?: "₹99/month"

        com.foodcheck.presentation.components.PaywallSheet(
            priceFormatted = price,
            isLoading = isLoading,
            errorMessage = errorMsg,
            onSubscribeClick = {
                com.foodcheck.analytics.getAnalyticsTracker().logPaywallSubscribeClicked("pro_monthly", price, "app_global_paywall")
                billingManager.launchSubscriptionFlow("pro_monthly")
            },
            onDismiss = { viewModel.dismissPaywall("app_global_paywall") }
        )
    }
}
