package io.spixy.imageaggregator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.Duration.Companion.milliseconds

private val log = KotlinLogging.logger {}

object RandomQueue {
    private val queue = mutableListOf<() -> Unit>()

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "RandomQueue started" }
        while (true) {
            if(queue.isNotEmpty()) {
                log.info { "RandomQueue size: ${queue.size}" }
                val task = queue.random()
                queue.remove(task)
                task.invoke()
            }
            delay(500.milliseconds)
        }
    }

    fun add(block: () -> Unit) {
        queue.add(block)
    }
}