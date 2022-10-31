package io.spixy.imageaggregator

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import io.spixy.imageaggregator.scraper.JoyreactorScraper
import io.spixy.imageaggregator.scraper.RedditScraper
import io.spixy.imageaggregator.web.WebUI
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}

fun main() {
    val config = Yaml.default.decodeFromStream<Config>(File("config.yml").inputStream().buffered())
    runBlocking {
        config.joyreactor?.let { JoyreactorScraper(it).start(this) }
        config.reddit?.let { RedditScraper(it).start(this) }
        RunnableRandomQueue.start(this)
        WebUI(config.webUi).start(this)
    }
    log.info { "App closed" }
}