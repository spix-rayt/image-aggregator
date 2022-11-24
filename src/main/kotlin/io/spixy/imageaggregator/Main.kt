package io.spixy.imageaggregator

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import io.spixy.imageaggregator.scraper.JoyreactorScraper
import io.spixy.imageaggregator.scraper.RedditScraper
import io.spixy.imageaggregator.scraper.TelegramScraper
import io.spixy.imageaggregator.scraper.VKScraper
import io.spixy.imageaggregator.web.ImageSimilarityService
import io.spixy.imageaggregator.web.WebUIController
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}

fun main() {
    val config = Yaml.default.decodeFromStream<Config>(File("config.yml").inputStream().buffered())
    runBlocking {
        config.joyreactor?.let { JoyreactorScraper(it).start(this) }
        config.reddit?.let { RedditScraper(it).start(this) }
        config.vk?.let { VKScraper(it).start(this) }
        val telegramScraper = config.telegram?.let {
            TelegramScraper(it).apply { start(this@runBlocking) }
        }
        RunnableRandomQueue.start(this)
        ImageSimilarityService.start(this)
        WebUIController(config.webUi, telegramScraper).start(this)
    }
    log.info { "App closed" }
}