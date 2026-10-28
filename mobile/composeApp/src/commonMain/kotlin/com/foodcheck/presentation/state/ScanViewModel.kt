package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.dto.CatalogBrandDto
import com.foodcheck.data.dto.CatalogCompanyDto
import com.foodcheck.data.repositories.ShelfRepository
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.entities.ScanQuota
import com.foodcheck.domain.entities.ScanQuotaExceededException
import com.foodcheck.domain.entities.expandWithLabelOrder
import com.foodcheck.domain.usecases.ScanUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val SCAN_STATUS_MESSAGES = listOf(
    "Reading label...",
    "Extracting ingredients...",
    "Matching against database...",
    "Almost done..."
)

class ScanViewModel(
    private val scanUseCase: ScanUseCase,
    private val network: NetworkDataSource,
    private val shelfRepository: ShelfRepository,
) : ViewModel() {

    private val _outcome = MutableStateFlow<ScanOutcome?>(null)
    val outcome: StateFlow<ScanOutcome?> = _outcome.asStateFlow()

    private val _quota = MutableStateFlow<ScanQuota?>(null)
    val quota: StateFlow<ScanQuota?> = _quota.asStateFlow()

    private val _quotaExceeded = MutableStateFlow<ScanQuota?>(null)
    val quotaExceeded: StateFlow<ScanQuota?> = _quotaExceeded.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _statusText = MutableStateFlow(SCAN_STATUS_MESSAGES.first())
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        loadQuota()
    }

    fun loadQuota() {
        viewModelScope.launch {
            try {
                val q = network.getEntitlement()
                _quota.value = q
                if (q.adsSuppressed || q.isPro) {
                    com.foodcheck.ads.AdsController.adsSuppressed = true
                }
            } catch (_: Exception) {
                // Keep local or null
            }
        }
    }

    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall.asStateFlow()
    private var paywallOpenedAt: kotlin.time.TimeMark? = null

    fun openPaywall(trigger: String = "quota_dialog") {
        paywallOpenedAt = kotlin.time.TimeSource.Monotonic.markNow()
        com.foodcheck.analytics.getAnalyticsTracker().logPaywallViewed(trigger, "₹99/month")
        _showPaywall.value = true
    }

    fun dismissPaywall(trigger: String = "paywall") {
        val durationSeconds = paywallOpenedAt?.elapsedNow()?.inWholeSeconds ?: 0L
        com.foodcheck.analytics.getAnalyticsTracker().logPaywallDismissed(trigger, durationSeconds)
        _showPaywall.value = false
    }

    fun onPurchaseSuccessful(purchaseToken: String, productId: String) {
        viewModelScope.launch {
            com.foodcheck.analytics.getAnalyticsTracker().logSubscriptionSyncStarted(productId)
            try {
                val ok = network.activateSubscription(purchaseToken, productId)
                if (ok) {
                    loadQuota()
                    _showPaywall.value = false
                    _actionMessage.value = "Welcome to FoodCheck Pro! 50 AI scans unlocked."
                    com.foodcheck.analytics.getAnalyticsTracker().logSubscriptionSyncSuccess(productId, "pro", 50)
                    com.foodcheck.analytics.getAnalyticsTracker().setUserProperty("user_plan", "pro")
                }
            } catch (e: Exception) {
                com.foodcheck.analytics.getAnalyticsTracker().logSubscriptionSyncFailed(productId, e.message ?: "unknown")
                _error.value = "Failed to sync subscription with server: ${e.message}"
            }
        }
    }

    fun dismissQuotaExceeded(trigger: String = "quota_dialog") {
        com.foodcheck.analytics.getAnalyticsTracker().logQuotaDialogDismissed(trigger)
        _quotaExceeded.value = null
    }

    fun showQuotaDialog(trigger: String = "quota_badge") {
        val currentQuota = _quota.value
        com.foodcheck.analytics.getAnalyticsTracker().logQuotaDialogShown(
            remaining = currentQuota?.remaining ?: 0,
            limit = currentQuota?.limit ?: 5,
            trigger = trigger
        )
        _quotaExceeded.value = currentQuota
    }

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _brandSuggestions = MutableStateFlow<List<CatalogBrandDto>>(emptyList())
    val brandSuggestions: StateFlow<List<CatalogBrandDto>> = _brandSuggestions.asStateFlow()

    private val _companySuggestions = MutableStateFlow<List<CatalogCompanyDto>>(emptyList())
    val companySuggestions: StateFlow<List<CatalogCompanyDto>> = _companySuggestions.asStateFlow()

    private var lastImages: List<ByteArray> = emptyList()
    private var brandSearchJob: Job? = null
    private var companySearchJob: Job? = null

    fun scan(images: List<ByteArray>, source: String = "camera") {
        lastImages = images
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _outcome.value = null
            val startTime = kotlin.time.TimeSource.Monotonic.markNow()
            com.foodcheck.analytics.getAnalyticsTracker().logScanStarted(source, images.size)
            val statusJob = launch {
                for (message in SCAN_STATUS_MESSAGES) {
                    _statusText.value = message
                    delay(1800)
                }
            }
            scanUseCase(images)
                .onSuccess {
                    _outcome.value = it
                    val durationMs = startTime.elapsedNow().inWholeMilliseconds
                    val totalCount = it.labelIngredients().size
                    val flaggedCount = it.labelIngredients().count { ing -> ing.severity in setOf("red", "amber") || ing.showsWatch }
                    com.foodcheck.analytics.getAnalyticsTracker().logScanCompleted(
                        flaggedCount = flaggedCount,
                        ingredientCount = totalCount,
                        isCached = false,
                        durationMs = durationMs
                    )
                    it.quota?.let { q ->
                        _quota.value = q
                        com.foodcheck.analytics.getAnalyticsTracker().setUserProperty("quota_remaining", q.remaining.toString())
                        com.foodcheck.analytics.getAnalyticsTracker().setUserProperty("user_plan", if (q.isPro) "pro" else "free")
                    }
                    _loading.value = false
                }
                .onFailure {
                    val durationMs = startTime.elapsedNow().inWholeMilliseconds
                    if (it is ScanQuotaExceededException) {
                        _quotaExceeded.value = it.quota
                        _quota.value = it.quota
                        com.foodcheck.analytics.getAnalyticsTracker().logQuotaLimitReached(it.quota.used, it.quota.limit, "scan")
                        com.foodcheck.analytics.getAnalyticsTracker().logQuotaDialogShown(it.quota.remaining, it.quota.limit, "scan_quota_exceeded")
                    } else {
                        val errMsg = it.message ?: "Scan failed"
                        _error.value = errMsg
                        com.foodcheck.analytics.getAnalyticsTracker().logScanFailed(source, errMsg, durationMs)
                    }
                    _loading.value = false
                }
            statusJob.cancel()
            _statusText.value = SCAN_STATUS_MESSAGES.first()
        }
    }

    fun searchBrands(query: String) {
        brandSearchJob?.cancel()
        brandSearchJob = viewModelScope.launch {
            delay(180)
            try {
                _brandSuggestions.value = network.listBrands(query.trim()).brands
            } catch (_: Exception) {
                _brandSuggestions.value = emptyList()
            }
        }
    }

    fun searchCompanies(query: String) {
        companySearchJob?.cancel()
        companySearchJob = viewModelScope.launch {
            delay(180)
            try {
                _companySuggestions.value = network.listCompanies(query.trim()).companies
            } catch (_: Exception) {
                _companySuggestions.value = emptyList()
            }
        }
    }

    fun saveToShelf(
        displayName: String,
        brand: String?,
        brandId: Int? = null,
        companyIds: List<Int> = emptyList(),
        stance: String = "unsure",
        tags: List<String> = emptyList(),
    ) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _actionMessage.value = null
            try {
                val ings = _outcome.value?.labelIngredients().orEmpty()
                network.saveToShelf(
                    displayName = displayName.trim().ifBlank { "My scan" },
                    brand = brand?.trim()?.ifBlank { null },
                    brandId = brandId,
                    companyIds = companyIds.distinct(),
                    stance = stance,
                    tags = tags.distinct(),
                    ingredientsText = ings.joinToString(", ") { it.name },
                    snapshotJson = snapshotJson(ings),
                    image = lastImages.firstOrNull(),
                )
                runCatching { shelfRepository.invalidateAndRefresh() }
                _actionMessage.value = "Saved to your list"
                com.foodcheck.analytics.getAnalyticsTracker().logShelfItemSaved(brand, stance)
            } catch (e: Exception) {
                _actionMessage.value = e.message ?: "Could not save"
            } finally {
                _saving.value = false
            }
        }
    }

    fun publishStory(caption: String) {
        viewModelScope.launch {
            try {
                val ings = _outcome.value?.labelIngredients().orEmpty()
                network.publishStory(
                    caption = caption.trim(),
                    ingredientsText = ings.joinToString(", ") { it.name },
                    snapshotJson = snapshotJson(ings),
                    image = lastImages.firstOrNull(),
                )
                _actionMessage.value = "Posted to the feed"
            } catch (e: Exception) {
                _actionMessage.value = e.message ?: "Could not post"
            }
        }
    }

    /** Photos behind the current outcome (front first when two were taken). */
    fun capturedImages(): List<ByteArray> = lastImages

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clear() {
        _outcome.value = null
        _error.value = null
        _actionMessage.value = null
        _saving.value = false
        lastImages = emptyList()
        _brandSuggestions.value = emptyList()
        _companySuggestions.value = emptyList()
    }

    private fun snapshotJson(ings: List<AnnotatedIngredient>): String {
        // Label position travels with each entry so a saved pack can be compared by label order.
        val flat = ings.expandWithLabelOrder()
        val parts = flat.joinToString(",") { ing ->
            val id = ing.id?.replace("\"", "")
            val name = ing.name.replace("\\", "\\\\").replace("\"", "\\\"")
            val order = if (ing.order > 0) ",\"order\":${ing.order}" else ""
            if (id != null) """{"id":"$id","name":"$name"$order}""" else """{"id":null,"name":"$name"$order}"""
        }
        return "[$parts]"
    }
}
