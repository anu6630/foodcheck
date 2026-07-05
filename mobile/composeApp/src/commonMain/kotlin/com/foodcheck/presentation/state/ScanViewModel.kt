package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.usecases.ScanUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Progressive status copy shown while the multi-tool-call scan agent request is in flight. */
private val SCAN_STATUS_MESSAGES = listOf(
    "Reading label...",
    "Extracting ingredients...",
    "Matching against database...",
    "Almost done..."
)

class ScanViewModel(
    private val scanUseCase: ScanUseCase
) : ViewModel() {

    private val _outcome = MutableStateFlow<ScanOutcome?>(null)
    val outcome: StateFlow<ScanOutcome?> = _outcome.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _statusText = MutableStateFlow(SCAN_STATUS_MESSAGES.first())
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun scan(images: List<ByteArray>) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _outcome.value = null

            val statusJob = launch {
                for (message in SCAN_STATUS_MESSAGES) {
                    _statusText.value = message
                    delay(1800)
                }
            }

            scanUseCase(images)
                .onSuccess {
                    _outcome.value = it
                    _loading.value = false
                }
                .onFailure {
                    _error.value = it.message ?: "Scan failed"
                    _loading.value = false
                }
            statusJob.cancel()
            _statusText.value = SCAN_STATUS_MESSAGES.first()
        }
    }

    fun clear() {
        _outcome.value = null
        _error.value = null
    }
}
