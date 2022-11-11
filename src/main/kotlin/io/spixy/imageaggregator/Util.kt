package io.spixy.imageaggregator

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import java.io.File
import java.nio.file.Path

const val ANSI_RESET = "\u001B[0m"
const val ANSI_GREEN = "\u001B[32m"
const val ANSI_RED = "\u001B[31m"

fun String.paintGreen(): String {
    return "$ANSI_GREEN$this$ANSI_RESET"
}

fun String.paintRed(): String {
    return "$ANSI_RED$this$ANSI_RESET"
}

fun ByteArray.md5(): String {
    return DigestUtils(MessageDigestAlgorithms.MD5).digestAsHex(this)
}

fun File.md5(): String {
    return DigestUtils(MessageDigestAlgorithms.MD5).digestAsHex(this)
}

fun String.md5(): String {
    return DigestUtils(MessageDigestAlgorithms.MD5).digestAsHex(this)
}

fun String.isMd5Hash(): Boolean {
    if(this.length != 32) {
        return false
    }
    if(this.any { c -> !(c in '0'..'9' || c in 'a'..'f') }) {
        return false
    }
    return true
}

fun File.isChildOf(dir: Path): Boolean {
    return this.toPath().normalize().startsWith(dir.normalize())
}

fun File.alsoCreateIfNotExists(): File {
    if(!this.exists()) {
        parentFile.mkdirs()
        createNewFile()
    }
    return this
}

fun formatSize(v: Long): String {
    if (v < 1024) return "$v B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(v)) / 10
    return String.format("%.1f %siB", v.toDouble() / (1L shl z * 10), " KMGTPE"[z])
}

fun File.hasImageExtension(): Boolean {
    return this.extension == "jpg" || this.extension == "jpeg"
}

/**
 * for example:
 * oldBase = images/downloads
 * newBase = images/pass
 * file path should start with oldBase
 * file = images/downloads/joyreactor/tagname/123.jpg
 *
 * then result will be images/pass/joyreactor/tagname/123.jpg
 *
 * it similar to "images/downloads/joyreactor/tagname/123.jpg".replace("images/downloads", "images/pass")
 * but with path specific checks
 */
fun rebasePath(oldBase: Path, newBase: Path, file: File): File {
    require(file.parentFile.isChildOf(oldBase))
    var x = file
    val path = mutableListOf<String>()
    while (x.parentFile.isChildOf(oldBase)) {
        path.add(0, x.name)
        x = x.parentFile
    }
    var result = newBase.toFile()
    while (path.isNotEmpty()) {
        result = File(result, path.removeFirst())
    }
    return result
}

fun rebasePathWithOldBaseAutodetect(possibleOldBases: List<Path>, newBase: Path, file: File): File {
    val oldBase = possibleOldBases.first { file.isChildOf(it) }
    return rebasePath(oldBase, newBase, file)
}