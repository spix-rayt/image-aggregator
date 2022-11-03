package io.spixy.imageaggregator

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

object NewImageEventBus {
    private val mutableSharedFlow = MutableSharedFlow<File>(replay = 100)
    val events = mutableSharedFlow.asSharedFlow()

    suspend fun emitEvent(file: File) = mutableSharedFlow.emit(file)
}