package io.spixy.imageaggregator

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import java.io.File

const val ANSI_RESET = "\u001B[0m"
const val ANSI_GREEN = "\u001B[32m"

fun String.paintGreen(): String {
    return "$ANSI_GREEN$this$ANSI_RESET"
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

fun File.hasImageExtension(): Boolean {
    return this.extension == "jpg" || this.extension == "jpeg"
}