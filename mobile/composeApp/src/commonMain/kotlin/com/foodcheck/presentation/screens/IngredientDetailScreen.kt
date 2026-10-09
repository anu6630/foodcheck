package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.presentation.state.IngredientDetailViewModel
import androidx.compose.material.icons.filled.ChevronLeft
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun IngredientDetailScreen(
    ingredientId: String,
    onBack: () -> Unit,
    viewModel: IngredientDetailViewModel = koinViewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(ingredientId) { viewModel.load(ingredientId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        // No statusBarsPadding: Scaffold already insets content when global topBar is hidden.
    ) {
        // Detail-first header; LifeX brand secondary (global App topBar is hidden here).
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isIos = currentPlatform == Platform.IOS
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
            Column {
                Text(
                    "Ingredient Detail",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        ) {
                            append("Life")
                        }
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = AppColors.Primary.copy(alpha = 0.9f),
                            )
                        ) {
                            append("X")
                        }
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        ) {
                            append(" Food")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            error != null -> Text(text = error ?: "", color = MaterialTheme.colorScheme.error)
            detail != null -> {
                val ing = detail!!
                val uriHandler = LocalUriHandler.current
                val overallSeverity = when {
                    ing.bans.any { it.status == "banned" } -> "red"
                    ing.bans.any { it.status == "restricted" } -> "amber"
                    ing.bans.any { it.status == "permitted" } -> "green"
                    else -> "gray"
                }
                val displayLimits = ing.useLimits.take(80)

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(AppColors.severityColor(overallSeverity), shape = RoundedCornerShape(5.dp)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(ing.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                        }
                    }
                    val codeLine = ing.codesLabel
                        ?: ing.codes.joinToString(" · ") { it.display.ifBlank { "${it.system} ${it.code}" } }
                            .ifBlank { ing.eNumber }
                    if (codeLine != null) {
                        item {
                            Text("Codes: $codeLine", fontWeight = FontWeight.Bold, color = AppColors.SecondaryLight)
                        }
                    }
                    val category = ing.category
                    if (category != null) {
                        item {
                            Text("Category: ${category.uppercase()}", fontWeight = FontWeight.SemiBold, color = AppColors.TextMuted, fontSize = 12.sp)
                        }
                    }
                    item {
                        HorizontalDivider(color = AppColors.Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    }

                    if (ing.bans.isNotEmpty()) {
                        items(ing.bans) { ban ->
                            val banColor = AppColors.severityColor(
                                when (ban.status) {
                                    "banned" -> "red"
                                    "restricted" -> "amber"
                                    "permitted" -> "green"
                                    else -> "gray"
                                }
                            )
                            Surface(
                                color = banColor.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, banColor.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        ban.countryLabel?.takeIf { it.isNotBlank() } ?: ban.countryCode,
                                        fontWeight = FontWeight.Bold,
                                        color = banColor,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        when (ban.status.lowercase()) {
                                            "banned" -> "Banned"
                                            "restricted" -> "Restricted — extra conditions apply"
                                            "permitted" -> "Authorised"
                                            else -> ban.status
                                        },
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    if (ban.condition != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Condition: ${ban.condition}", fontSize = 13.sp, color = AppColors.TextFaint)
                                    }
                                    if (ban.regulationRef != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Ref: ${ban.regulationRef}", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "No regulation data in the ${ing.trackedCountries.size} countries we track" +
                                    " (${ing.trackedCountries.joinToString(", ")})." +
                                    " That is not a safety clearance — most catalog names (sugar, atta, oils)" +
                                    " simply have no country rule row.",
                                color = AppColors.TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (displayLimits.isNotEmpty()) {
                        item {
                            HorizontalDivider(color = AppColors.Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                        item {
                            Text(
                                "Where it can be used (${ing.useLimits.size} food categories)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            val countLine = ing.useLimitCounts.entries
                                .sortedBy { if (it.key == "IN") 0 else 1 }
                                .joinToString(" · ") { "${it.key} ${it.value}" }
                            Text(
                                if (countLine.isNotBlank()) {
                                    "Caps by food type. Showing a sample per country ($countLine). Numbers follow Codex GSFA / FSSAI Appendix A."
                                } else {
                                    "Numbers follow Codex GSFA food categories (the same scheme FSSAI Appendix A uses)."
                                },
                                color = AppColors.TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(displayLimits) { lim ->
                            val foodName = lim.foodCategoryName?.takeIf { it.isNotBlank() }
                            val code = lim.foodCategoryCode.takeIf { it.isNotBlank() && it != "GMP" }
                            Surface(
                                color = AppColors.Surface.copy(alpha = 0.4f),
                                border = BorderStroke(0.5.dp, AppColors.Border.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            foodName ?: code ?: (lim.countryLabel ?: lim.countryCode),
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                        val meta = listOfNotNull(
                                            lim.countryLabel ?: lim.countryCode,
                                            code
                                        ).joinToString(" · ")
                                        if (meta.isNotBlank() && meta != foodName) {
                                            Text(meta, color = AppColors.TextMuted, fontSize = 11.sp)
                                        }
                                        lim.note?.let { note ->
                                            Text(note, color = AppColors.TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                    Text(
                                        lim.maxLevel.ifBlank { "—" },
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AppColors.SecondaryLight,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        if (ing.useLimits.size > displayLimits.size) {
                            item {
                                Text(
                                    "and ${ing.useLimits.size - displayLimits.size} more categories",
                                    color = AppColors.TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (ing.sources.isNotEmpty()) {
                        item {
                            HorizontalDivider(color = AppColors.Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                        item {
                            Text("Sources", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text(
                                "The document and version we actually captured — not an implication that nothing newer exists.",
                                color = AppColors.TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(ing.sources) { src ->
                            Surface(
                                color = AppColors.Surface.copy(alpha = 0.4f),
                                border = BorderStroke(0.5.dp, AppColors.Border.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(src.title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
                                    val versionLine = listOfNotNull(
                                        src.version?.takeIf { it.isNotBlank() }?.let { "Version $it" },
                                        src.versionDate?.takeIf { it.isNotBlank() }?.let { "dated $it" },
                                        src.countryCode
                                    ).joinToString(" · ")
                                    if (versionLine.isNotBlank()) {
                                        Text(versionLine, color = AppColors.SecondaryLight, fontSize = 12.sp)
                                    }
                                    if (src.retrievedAt.isNotBlank()) {
                                        Text("Captured ${src.retrievedAt.take(10)}", color = AppColors.TextMuted, fontSize = 11.sp)
                                    }
                                    src.citation?.let { citation ->
                                        Text(citation, color = Color.Gray, fontSize = 11.sp)
                                    }
                                    val collected = src.url?.takeIf { it.isNotBlank() }
                                    if (collected != null) {
                                        Text(
                                            "Collected from: $collected",
                                            color = AppColors.Primary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable { uriHandler.openUri(collected) }
                                        )
                                    }
                                    val official = src.citationUrl?.takeIf { it.isNotBlank() && it != collected }
                                    if (official != null) {
                                        Text(
                                            "Official text: $official",
                                            color = AppColors.Primary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable { uriHandler.openUri(official) }
                                        )
                                    }
                                    src.files.forEach { file ->
                                        val sha = file.sha256.take(12)
                                        val kindLabel = when (file.kind) {
                                            "parsed" -> "Ingest extract"
                                            "historical" -> "Earlier capture"
                                            else -> "Archived copy"
                                        }
                                        val href = file.downloadUrl
                                        Text(
                                            "$kindLabel: ${file.filename}  sha256:$sha…",
                                            color = if (href != null) AppColors.Primary else AppColors.TextMuted,
                                            fontSize = 11.sp,
                                            modifier = if (href != null) Modifier.clickable { uriHandler.openUri(href) } else Modifier
                                        )
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
