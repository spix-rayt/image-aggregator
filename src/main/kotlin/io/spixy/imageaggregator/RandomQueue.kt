package io.spixy.imageaggregator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

object RandomQueue {
    private val queue = mutableListOf<suspend CoroutineScope.() -> Unit>()

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "RandomQueue started".paintGreen() }
        while (true) {
            if(queue.isNotEmpty()) {
                log.info { "RandomQueue size: ${queue.size}" }
                val task = queue.random()
                queue.remove(task)
                task.invoke(this)
            } else {
                log.info { "Queue empty. Waiting..." }
                while (queue.isEmpty()) {
                    delay(1.seconds)
                }
            }
            delay(500.milliseconds)
        }
    }

    fun add(block: suspend CoroutineScope.() -> Unit) {
        queue.add(block)
    }
}