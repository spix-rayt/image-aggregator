package io.spixy.imageaggregator.scraper

import io.spixy.imageaggregator.isMd5Hash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private val log = KotlinLogging.logger {}

abstract class Scraper {
    companion object {
        private val downloadedFile = File("images/download/downloaded.txt").also {
            if(!it.exists()) {
                it.parentFile.mkdirs()
                it.createNewFile()
            }
        }
        private val hashes = mutableSetOf<String>()

        init {
            downloadedFile.readLines().forEach { line ->
                if(line.isMd5Hash()) {
                    hashes.add(line)
                } else {
                    log.warn { "invalid line in $downloadedFile - $line" }
                }
                hashes.add(line)
            }
        }

        fun isUnknownHash(hash: String) = !hashes.contains(hash)

        fun registerHash(hash1: String, hash2: String) {
            check(hash1.isMd5Hash()) { "$hash1 is not md5 hash" }
            check(hash2.isMd5Hash()) { "$hash2 is not md5 hash" }
            hashes.add(hash1)
            hashes.add(hash2)
            downloadedFile.appendText("$hash1\n$hash2\n")
        }

        suspend fun OkHttpClient.downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
            log.info { "download $url" }
            val call = this@downloadImage.newCall(
                Request.Builder().url(url).build()
            )

            call.execute().use {
                if (it.headers["Content-type"]?.startsWith("image") == true) {
                    it.body?.bytes()
                } else {
                    null
                }
            }
        }
    }
}