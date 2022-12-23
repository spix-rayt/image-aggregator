package io.spixy.imageaggregator.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger {}

open class RestScraper : Scraper() {
    companion object {
        suspend fun OkHttpClient.downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
            log.info { "download $url" }
            retry(5) {
                val call = this@downloadImage.newCall(
                    Request.Builder().url(url).build()
                )

                call.execute().use {
                    if (it.headers["Content-type"]?.startsWith("image") == true) {
                        if(it.headers["Content-length"]?.toInt() != 0) {
                            it.body?.bytes()
                        } else {
                            log.warn { "Content length is 0. url = $url. header = ${it.headers["Content-length"]}" }
                            null
                        }
                    } else {
                        null
                    }
                }
            }
        }

        private suspend fun<T> retry(times: Int, block: () -> T): T? {
            for(i in 1..times) {
                try {
                    return block()
                } catch (e: Exception) {
                    log.error(e) {  }
                }
                log.info("waiting 1 minute for retry...")
                delay(1.minutes)
            }
            return null
        }
    }
}