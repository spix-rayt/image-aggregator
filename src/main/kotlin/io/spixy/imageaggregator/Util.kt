package io.spixy.imageaggregator

import mu.KotlinLogging
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import java.io.File

private val log = KotlinLogging.logger {}

const val ANSI_RESET = "\u001B[0m"
const val ANSI_GREEN = "\u001B[32m"

fun String.paintGreen(): String {
    return "$ANSI_GREEN$this$ANSI_RESET"
}

fun scanDirectory(root: File): DirectoryScanResult {
    log.info { "$root scanning" }
    val files = root.walk().filter { it.isFile }.toList()

    return DirectoryScanResult(
        files.asSequence()
            .onEachIndexed { index, _ ->
                if(index == 0 || index == files.lastIndex || index % 200 == 0) {
                    log.info { "Scan: $index/${files.lastIndex}" }
                }
            }
            .map { DigestUtils(MessageDigestAlgorithms.MD5).digestAsHex(it) }
            .toSet(),
        files.map { it.name }.toSet()
    )
}

data class DirectoryScanResult(val digests: Set<String>, val fileNames: Set<String>)