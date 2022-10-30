package io.spixy.imageaggregator

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}

fun main() {
    val config = Yaml.default.decodeFromStream<Config>(File("config.yml").inputStream().buffered())
    runBlocking {
        config.joyreactor?.let { JoyreactorScrapper(it).start(this) }
        config.reddit?.let { RedditScrapper(it).start(this) }
        RandomQueue.start(this)
    }
    log.info { "App closed" }
}