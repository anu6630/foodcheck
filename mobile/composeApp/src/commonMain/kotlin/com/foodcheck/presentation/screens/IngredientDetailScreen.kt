package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.DataSource
import com.foodcheck.domain.entities.IngredientBan
import com.foodcheck.domain.entities.IngredientDetail
import com.foodcheck.domain.entities.IngredientHealthReport
import com.foodcheck.domain.entities.IngredientUseLimit
import com.foodcheck.presentation.state.IngredientDetailViewModel
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform
import org.koin.compose.viewmodel.koinViewModel

private enum class IngredientDetailTab { Health, Regulation, More }

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
            .then(if (currentPlatform == Platform.IOS) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
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
                Text("Ingredient Detail", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))) {
                            append("Life")
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 12.sp, color = AppColors.Primary.copy(alpha = 0.9f))) {
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
            detail != null -> IngredientDetailBody(detail!!)
        }
    }
}

@Composable
private fun IngredientDetailBody(ing: IngredientDetail) {
    val uriHandler = LocalUriHandler.current
    val banSeverity = banSeverity(ing.bans)
    val healthSeverity = healthSeverity(ing.healthReports)
    val glanceHealth = healthGlanceLabel(ing.healthReports)
    val glanceMarkets = marketsGlanceLine(ing)
    val sourcePeek = healthSourcePeek(ing.healthReports)
    val grouped = remember(ing.healthReports) { groupHealthReports(ing.healthReports) }
    val countryRows = remember(ing) { countryStatusRows(ing) }

    var tab by remember {
        mutableStateOf(
            when {
                ing.healthReports.isNotEmpty() -> IngredientDetailTab.Health
                else -> IngredientDetailTab.Regulation
            }
        )
    }
    var expandedKinds by remember(ing.id) {
        mutableStateOf(setOf(grouped.firstOrNull()?.first ?: ""))
    }
    var showUseLimits by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(AppColors.severityColor(worstSeverity(banSeverity, healthSeverity)), RoundedCornerShape(5.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(ing.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
            }
        }
        val codeLine = ing.codesLabel
            ?: ing.codes.joinToString(" · ") { it.display.ifBlank { "${it.system} ${it.code}" } }
                .ifBlank { ing.eNumber }
        if (codeLine != null) {
            item {
                Text(codeLine, fontWeight = FontWeight.Bold, color = AppColors.SecondaryLight, fontSize = 13.sp)
            }
        }
        ing.category?.let { cat ->
            item {
                Text(cat.replaceFirstChar { it.uppercase() }, color = AppColors.TextMuted, fontSize = 12.sp)
            }
        }

        item {
            Text("At a glance", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlanceCard(
                    title = "Health",
                    body = glanceHealth,
                    sub = if (ing.healthReports.isEmpty()) "No reports linked"
                    else "${ing.healthReports.size} signal${if (ing.healthReports.size == 1) "" else "s"}",
                    accent = AppColors.severityColor(healthSeverity),
                    modifier = Modifier.weight(1f),
                    onClick = { tab = IngredientDetailTab.Health },
                )
                GlanceCard(
                    title = "In your markets",
                    body = glanceMarkets.first,
                    sub = glanceMarkets.second,
                    accent = AppColors.severityColor(banSeverity),
                    modifier = Modifier.weight(1f),
                    onClick = { tab = IngredientDetailTab.Regulation },
                )
            }
            if (sourcePeek.isNotBlank()) {
                Text(
                    sourcePeek,
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            DetailTabRow(selected = tab, onSelect = { tab = it })
        }

        when (tab) {
            IngredientDetailTab.Health -> {
                if (ing.healthReports.isEmpty()) {
                    item {
                        MutedNote("No health reports linked yet. That is not a safety clearance.")
                    }
                } else {
                    item {
                        Text(
                            "Grouped by topic. Each row keeps its publisher — same topic from two sources stays two cards.",
                            color = AppColors.TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    grouped.forEach { (kind, reports) ->
                        val open = kind in expandedKinds
                        item(key = "kind-$kind") {
                            HealthKindHeader(
                                kind = kind,
                                count = reports.size,
                                expanded = open,
                                onToggle = {
                                    expandedKinds = if (open) expandedKinds - kind else expandedKinds + kind
                                },
                            )
                        }
                        if (open) {
                            items(reports, key = { it.id }) { report ->
                                HealthReportCard(report, uriHandler)
                            }
                        }
                    }
                }
            }

            IngredientDetailTab.Regulation -> {
                item {
                    Text("Status by country", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text(
                        "Country law / authorisation — separate from health reports above.",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }
                if (countryRows.isEmpty()) {
                    item {
                        MutedNote(
                            "No regulation data in the ${ing.trackedCountries.size} countries we track" +
                                " (${ing.trackedCountries.joinToString(", ")})." +
                                " That is not a safety clearance."
                        )
                    }
                } else {
                    items(countryRows, key = { it.code }) { row ->
                        CountryStatusRow(row)
                    }
                }
            }

            IngredientDetailTab.More -> {
                item {
                    DisclosureHeader(
                        title = "Where it can be used (${ing.useLimits.size} food categories)",
                        expanded = showUseLimits,
                        enabled = ing.useLimits.isNotEmpty(),
                        onToggle = { showUseLimits = !showUseLimits },
                    )
                }
                if (showUseLimits && ing.useLimits.isNotEmpty()) {
                    item {
                        val countLine = ing.useLimitCounts.entries
                            .sortedBy { if (it.key == "IN") 0 else 1 }
                            .joinToString(" · ") { "${it.key} ${it.value}" }
                        Text(
                            if (countLine.isNotBlank()) {
                                "Caps by food type. Sample per country ($countLine)."
                            } else {
                                "Numbers follow Codex GSFA / FSSAI Appendix A."
                            },
                            color = AppColors.TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    items(ing.useLimits.take(80), key = { "${it.countryCode}-${it.foodCategoryCode}-${it.maxLevel}" }) { lim ->
                        UseLimitRow(lim)
                    }
                    if (ing.useLimits.size > 80) {
                        item {
                            Text("and ${ing.useLimits.size - 80} more categories", color = AppColors.TextMuted, fontSize = 12.sp)
                        }
                    }
                }

                item {
                    DisclosureHeader(
                        title = "Source documents (${ing.sources.size})",
                        expanded = showSources,
                        enabled = ing.sources.isNotEmpty(),
                        onToggle = { showSources = !showSources },
                    )
                    if (ing.sources.isNotEmpty() && !showSources) {
                        Text(
                            "Archived copies of what we captured for the claims on this ingredient.",
                            color = AppColors.TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (showSources) {
                    items(ing.sources, key = { it.id }) { src ->
                        SourceCard(src, uriHandler)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun GlanceCard(
    title: String,
    body: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = AppColors.Surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = AppColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(accent, RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(body, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(sub, color = AppColors.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailTabRow(selected: IngredientDetailTab, onSelect: (IngredientDetailTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IngredientDetailTab.entries.forEach { tab ->
            val on = tab == selected
            Surface(
                color = if (on) AppColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
            ) {
                Text(
                    tab.name,
                    modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (on) AppColors.Primary else AppColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun HealthKindHeader(kind: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val accent = healthKindAccent(kind)
    Surface(
        color = AppColors.Surface.copy(alpha = 0.4f),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (expanded) "▼" else "▶", color = accent, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                healthKindTitle(kind),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text("($count)", color = AppColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HealthReportCard(report: IngredientHealthReport, uriHandler: UriHandler) {
    val accent = healthKindAccent(report.kind)
    Surface(
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(report.summary, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
            val valueLine = listOfNotNull(
                report.value?.takeIf { it.isNotBlank() },
                report.unit?.takeIf { it.isNotBlank() }
            ).joinToString(" ")
            if (valueLine.isNotBlank()) {
                Text(valueLine, color = AppColors.SecondaryLight, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            val sourceLine = listOfNotNull(
                report.sourcePublisher?.takeIf { it.isNotBlank() },
                report.sourceTitle?.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { report.sourceId }
            Text(sourceLine, color = AppColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            report.evaluationYear?.takeIf { it.isNotBlank() }?.let {
                Text("Eval $it", color = AppColors.TextMuted, fontSize = 11.sp)
            }
            report.citationUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Text(
                    "Open ↗",
                    color = AppColors.Primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable { uriHandler.openUri(url) }
                )
            }
        }
    }
}

private data class CountryRow(
    val code: String,
    val label: String,
    val status: String,
    val condition: String?,
    val regulationRef: String?,
)

@Composable
private fun CountryStatusRow(row: CountryRow) {
    val severity = when (row.status.lowercase()) {
        "banned" -> "red"
        "restricted" -> "amber"
        "permitted" -> "green"
        else -> "gray"
    }
    val color = AppColors.severityColor(severity)
    val statusLabel = when (row.status.lowercase()) {
        "banned" -> "Banned"
        "restricted" -> "Restricted"
        "permitted" -> "Authorised"
        "no_data" -> "No data"
        else -> row.status
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.code, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            row.condition?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = AppColors.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(statusLabel, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DisclosureHeader(title: String, expanded: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (!enabled) "·" else if (expanded) "▼" else "▶",
            color = AppColors.TextMuted,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else AppColors.TextMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun UseLimitRow(lim: IngredientUseLimit) {
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
                val meta = listOfNotNull(lim.countryLabel ?: lim.countryCode, code).joinToString(" · ")
                if (meta.isNotBlank() && meta != foodName) {
                    Text(meta, color = AppColors.TextMuted, fontSize = 11.sp)
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

@Composable
private fun SourceCard(src: DataSource, uriHandler: UriHandler) {
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

@Composable
private fun MutedNote(text: String) {
    Text(text, color = AppColors.TextMuted, fontSize = 13.sp)
}

private fun banSeverity(bans: List<IngredientBan>): String = when {
    bans.any { it.status == "banned" } -> "red"
    bans.any { it.status == "restricted" } -> "amber"
    bans.any { it.status == "permitted" } -> "green"
    else -> "gray"
}

private fun healthSeverity(reports: List<IngredientHealthReport>): String {
    if (reports.isEmpty()) return "gray"
    val kinds = reports.map { it.kind.lowercase() }
    return when {
        kinds.any {
            it in setOf(
                "carcinogen", "iarc_2b", "iarc_2b_banned_many", "cancer_precursor_concern",
                "authorisation_withdrawn", "not_authorised_eu"
            ) || (it == "genotox" && reports.any { r ->
                r.kind.equals("genotox", true) && r.value.equals("Positive", true)
            })
        } -> "amber"
        kinds.any {
            it in setOf("adi", "overexposure_risk", "anses_of_interest", "label_warning_hyperactivity", "genotox")
        } -> "amber"
        else -> "green"
    }
}

private fun worstSeverity(a: String, b: String): String {
    val rank = mapOf("red" to 0, "amber" to 1, "green" to 2, "gray" to 3)
    return listOf(a, b).minBy { rank[it] ?: 9 }
}

private fun healthGlanceLabel(reports: List<IngredientHealthReport>): String {
    if (reports.isEmpty()) return "No data"
    val kinds = reports.map { it.kind.lowercase() }.toSet()
    return when {
        kinds.any {
            it in setOf(
                "carcinogen", "iarc_2b", "iarc_2b_banned_many", "cancer_precursor_concern",
                "authorisation_withdrawn", "not_authorised_eu"
            )
        } -> "Watch"
        kinds.any { it in setOf("genotox", "overexposure_risk", "label_warning_hyperactivity") } -> "Watch"
        kinds.contains("adi") || kinds.contains("anses_of_interest") -> "Notes"
        else -> "Info"
    }
}

private fun marketsGlanceLine(ing: IngredientDetail): Pair<String, String> {
    val bans = ing.bans
    val tracked = ing.trackedCountries.size
    if (bans.isEmpty()) return "No data" to "$tracked countries tracked"
    val banned = bans.count { it.status == "banned" }
    val restricted = bans.count { it.status == "restricted" }
    val authorised = bans.count { it.status == "permitted" }
    val body = when {
        banned > 0 -> "$banned banned"
        restricted > 0 -> "$restricted restricted"
        authorised > 0 -> "Authorised"
        else -> "Mixed"
    }
    val bits = buildList {
        if (authorised > 0 && banned == 0) add("$authorised authorised")
        if (restricted > 0) add("$restricted restricted")
        if (banned > 0) add("$banned banned")
        add("$tracked tracked")
    }
    return body to bits.joinToString(" · ")
}

private fun healthSourcePeek(reports: List<IngredientHealthReport>): String {
    val labels = reports.mapNotNull { r ->
        when {
            r.sourcePublisher?.contains("IARC", true) == true -> "IARC"
            r.sourcePublisher?.contains("EFSA", true) == true -> "EFSA"
            r.sourcePublisher?.contains("JECFA", true) == true -> "JECFA"
            r.sourcePublisher?.contains("Open Food", true) == true -> "Open Food Facts"
            r.kind.contains("adi", true) && r.value != null -> "ADI"
            r.kind.contains("carcinogen", true) || r.value == "2B" || r.value == "2A" || r.value == "1" ->
                "IARC ${r.value}"
            else -> r.sourcePublisher?.substringBefore(" /")?.take(18)
        }
    }.distinct().take(5)
    return labels.joinToString(" · ")
}

private fun healthKindTitle(kind: String): String = when (kind.lowercase()) {
    "carcinogen", "iarc_2b", "iarc_2b_banned_many" -> "Carcinogen"
    "cancer_precursor_concern" -> "Cancer-related concern"
    "authorisation_withdrawn", "not_authorised_eu" -> "Authorisation"
    "genotox" -> "Genotoxicity"
    "adi" -> "Acceptable daily intake"
    "overexposure_risk" -> "Overexposure risk"
    "anses_of_interest" -> "Agency interest list"
    "label_warning_hyperactivity" -> "Label warning"
    "evaluation" -> "Evaluation"
    else -> kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun healthKindAccent(kind: String): Color = when (kind.lowercase()) {
    "carcinogen", "iarc_2b", "iarc_2b_banned_many", "cancer_precursor_concern",
    "authorisation_withdrawn", "not_authorised_eu" -> AppColors.severityColor("red")
    "genotox", "adi", "overexposure_risk", "anses_of_interest", "label_warning_hyperactivity" ->
        AppColors.severityColor("amber")
    else -> AppColors.SecondaryLight
}

private fun healthKindRank(kind: String): Int = when (kind.lowercase()) {
    "carcinogen", "iarc_2b", "iarc_2b_banned_many" -> 0
    "cancer_precursor_concern" -> 1
    "authorisation_withdrawn", "not_authorised_eu" -> 2
    "genotox" -> 3
    "adi" -> 4
    "overexposure_risk", "label_warning_hyperactivity" -> 5
    "anses_of_interest" -> 6
    "evaluation" -> 7
    else -> 8
}

private fun groupKey(kind: String): String = when (kind.lowercase()) {
    "iarc_2b", "iarc_2b_banned_many" -> "carcinogen"
    "not_authorised_eu" -> "authorisation_withdrawn"
    else -> kind.lowercase()
}

private fun groupHealthReports(reports: List<IngredientHealthReport>): List<Pair<String, List<IngredientHealthReport>>> =
    reports
        .groupBy { groupKey(it.kind) }
        .toList()
        .sortedBy { healthKindRank(it.first) }

private fun countryStatusRows(ing: IngredientDetail): List<CountryRow> {
    val byCode = ing.bans.associateBy { it.countryCode.uppercase() }
    val order = ing.trackedCountries.map { it.uppercase() }.distinct()
    val extras = byCode.keys.filter { it !in order }
    return (order + extras).map { code ->
        val ban = byCode[code]
        CountryRow(
            code = code,
            label = ban?.countryLabel?.takeIf { it.isNotBlank() } ?: countryLabel(code),
            status = ban?.status ?: "no_data",
            condition = ban?.condition,
            regulationRef = ban?.regulationRef,
        )
    }
}

private fun countryLabel(code: String): String = when (code.uppercase()) {
    "IN" -> "India"
    "EU" -> "European Union"
    "US" -> "United States"
    "AU" -> "Australia"
    "NZ" -> "New Zealand"
    "GB" -> "United Kingdom"
    "CA" -> "Canada"
    "KR" -> "South Korea"
    "JP" -> "Japan"
    else -> code
}
