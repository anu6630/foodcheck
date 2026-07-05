package com.foodcheck.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class FlowWatcher<T>(
    private val flow: StateFlow<T>,
    private val onChange: (T) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val job: Job = flow.onEach { onChange(it) }.launchIn(scope)

    fun close() {
        job.cancel()
    }
}
