package com.foodcheck.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.presentation.navigation.Route
import com.foodcheck.presentation.screens.IngredientDetailScreen
import com.foodcheck.presentation.screens.LandingScreen
import com.foodcheck.presentation.screens.ProductDetailScreen
import com.foodcheck.presentation.screens.ScanCaptureScreen
import com.foodcheck.presentation.screens.ScanSuggestionsScreen
import com.foodcheck.presentation.screens.SearchResultsScreen
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.util.ImagePickerLauncher
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(imagePickerLauncher: ImagePickerLauncher) {
    val navController = rememberNavController()
    val isIos = currentPlatform == Platform.IOS

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
        Scaffold(
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isIos) Color.White.copy(alpha = 0.03f) else AppColors.Surface.copy(alpha = 0.85f),
                    border = if (isIos) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)) else null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        if (isIos) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))) {
                                        append("life")
                                    }
                                    withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 13.sp, color = AppColors.Primary)) {
                                        append("Ex")
                                    }
                                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = Color.White)) {
                                        append(" Food")
                                    }
                                },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(AppColors.Primary, shape = RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))) {
                                            append("life")
                                        }
                                        withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 15.sp, color = AppColors.Primary)) {
                                            append("Ex")
                                        }
                                        withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = Color.White)) {
                                            append(" Food")
                                        }
                                    }
                                )
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
                        LandingScreen(
                            onScanClick = { navController.navigate(Route.ScanGraph) },
                            onSearchClick = { navController.navigate(Route.SearchResults()) }
                        )
                    }
                    composable<Route.SearchResults> { entry ->
                        val route = entry.toRoute<Route.SearchResults>()
                        SearchResultsScreen(
                            initialQuery = route.initialQuery,
                            onProductClick = { result -> onProductClick(result.product.barcode) },
                            onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } }
                        )
                    }
                    navigation<Route.ScanGraph>(startDestination = Route.ScanCapture) {
                        composable<Route.ScanCapture> { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry<Route.ScanGraph>() }
                            val scanViewModel: ScanViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
                            ScanCaptureScreen(
                                imagePickerLauncher = imagePickerLauncher,
                                viewModel = scanViewModel,
                                onScanOutcome = { outcome ->
                                    when (outcome) {
                                        is ScanOutcome.Confident -> {
                                            if (isIos) {
                                                navController.popBackStack(Route.Landing, false)
                                                onProductClick(outcome.product.product.barcode)
                                            } else {
                                                navController.navigate(Route.ProductDetail(outcome.product.product.barcode)) {
                                                    popUpTo(Route.Landing)
                                                }
                                            }
                                        }
                                        else -> navController.navigate(Route.ScanSuggestions)
                                    }
                                }
                            )
                        }
                        composable<Route.ScanSuggestions> { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry<Route.ScanGraph>() }
                            val scanViewModel: ScanViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
                            ScanSuggestionsScreen(
                                viewModel = scanViewModel,
                                onCandidateClick = { candidate ->
                                    if (isIos) {
                                        navController.popBackStack(Route.Landing, false)
                                        onProductClick(candidate.product.barcode)
                                    } else {
                                        navController.navigate(Route.ProductDetail(candidate.product.barcode)) {
                                            popUpTo(Route.Landing)
                                        }
                                    }
                                },
                                onIngredientClick = { ing -> ing.id?.let { onIngredientClick(it) } }
                            )
                        }
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
