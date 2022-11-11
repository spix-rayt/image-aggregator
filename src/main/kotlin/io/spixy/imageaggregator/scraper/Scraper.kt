package io.spixy.imageaggregator.scraper

import io.spixy.imageaggregator.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private val log = KotlinLogging.logger {}

abstract class Scraper {
    companion object {
        private val knownHashesDataFile = File("data/known_hashes.txt").alsoCreateIfNotExists()
        private val hashes = mutableSetOf<String>()

        init {
            knownHashesDataFile.readLines().forEach { line ->
                if(line.isMd5Hash()) {
                    hashes.add(line)
                } else {
                    log.warn { "invalid line in $knownHashesDataFile - $line" }
                }
                hashes.add(line)
            }
        }

        fun isUnknownHash(hash: String) = !hashes.contains(hash)

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

        suspend fun writeFile(file: File, bytes: ByteArray, fileBytesHash: String, digest: String) {
            if (isUnknownHash(fileBytesHash)) {
                if (file.exists()) {
                    error("$file already exists")
                }
                if (file.hasImageExtension()) {
                    file.parentFile.mkdirs()
                    file.writeBytes(bytes)
                    NewImageEventBus.emitEvent(file)
                    registerHash(fileBytesHash, digest)
                    log.info { "$file saved".paintGreen() }
                }
            } else {
                registerHash(digest)
            }
        }

        private fun registerHash(hash: String) {
            check(hash.isMd5Hash()) { "$hash is not md5 hash" }
            hashes.add(hash)
            knownHashesDataFile.appendText("$hash\n")
        }

        private fun registerHash(hash1: String, hash2: String) {
            check(hash1.isMd5Hash()) { "$hash1 is not md5 hash" }
            check(hash2.isMd5Hash()) { "$hash2 is not md5 hash" }
            hashes.add(hash1)
            hashes.add(hash2)
            knownHashesDataFile.appendText("$hash1\n$hash2\n")
        }
    }
}