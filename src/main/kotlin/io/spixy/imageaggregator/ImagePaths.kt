package io.spixy.imageaggregator

import java.io.File
import java.nio.file.Path

object ImagePaths {
    val IMAGES_DIR: Path = File("images").toPath().normalize()
    val DOWNLOAD_DIR: Path = File("images/download").toPath().normalize()
    val REDDIT_DOWNLOAD_DIR: Path = File("images/download/reddit").toPath().normalize()
    val JOYREACTOR_DOWNLOAD_DIR: Path = File("images/download/joyreactor").toPath().normalize()
    val PASS_DIR: Path = File("images/pass").toPath().normalize()
    val REDDIT_PASS_DIR: Path = File("images/pass/reddit").toPath().normalize()
    val JOYREACTOR_PASS_DIR: Path = File("images/pass/joyreactor").toPath().normalize()
    val TRASH_DIR: Path = File("images/trash").toPath().normalize()
    val REDDIT_TRASH_DIR: Path = File("images/trash/reddit").toPath().normalize()
    val JOYREACTOR_TRASH_DIR: Path = File("images/trash/joyreactor").toPath().normalize()
}