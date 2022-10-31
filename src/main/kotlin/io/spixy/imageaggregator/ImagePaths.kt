package io.spixy.imageaggregator

import java.io.File
import java.nio.file.Path

object ImagePaths {
    val DOWNLOAD_DIR: Path = File("images/download").toPath().normalize()
    val PASS_DIR: Path = File("images/pass").toPath().normalize()
    val TRASH_DIR: Path = File("images/trash").toPath().normalize()
}