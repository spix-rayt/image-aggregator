package io.spixy.imageaggregator.scraper

import io.spixy.imageaggregator.*
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files

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

        suspend fun moveFile(src: File, dst: File, fileBytesHash: String, digest: String) {
            if (isUnknownHash(fileBytesHash)) {
                if(dst.exists()) {
                    error("$dst already exists")
                }
                if(src.hasImageExtension()) {
                    dst.parentFile.mkdirs()
                    Files.move(src.toPath(), dst.toPath())
                    NewImageEventBus.emitEvent(dst)
                    registerHash(fileBytesHash, digest)
                    log.info { "$dst saved".paintGreen() }
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