package io.spixy.imageaggregator.web

import io.spixy.imageaggregator.NewImageEventBus
import io.spixy.imageaggregator.ImagePaths
import io.spixy.imageaggregator.hasImageExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.name

private val log = KotlinLogging.logger {}

class ImageScreenOutService(coroutineScope: CoroutineScope) {
    private val images = ImagePaths.DOWNLOAD_DIR.toFile().walk()
        .filter { it.isFile }
        .filter { it.hasImageExtension() }
        .toMutableList()
    private var currentImage: File? = null

    fun getCurrent(): File? {
        if (currentImage == null) {
            currentImage = images.randomOrNull()
        }
        return currentImage
    }

    init {
        coroutineScope.launch {
            NewImageEventBus.events
                .filter { it.toPath().normalize().startsWith(ImagePaths.DOWNLOAD_DIR) }
                .collect { file ->
                    images.add(file)
                }
        }
    }

    suspend fun passAndGoNext() {
        currentImage?.let { currentImage ->
            val newPath = buildNewImageDir(currentImage, ImagePaths.PASS_DIR.name)
            newPath.parentFile.mkdirs()
            if(currentImage.exists()) {
                log.info { "Move image $currentImage to $newPath" }
                if(newPath.exists()) {
                    log.warn { "$newPath already exists. overwriting..." }
                }
                Files.move(currentImage.toPath(), newPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                NewImageEventBus.emitEvent(newPath)
                images.remove(currentImage)
            } else {
                log.warn { "Image $currentImage not exists" }
            }
        }
        currentImage = images.randomOrNull()
    }

    suspend fun delete() {
        currentImage?.let { currentImage ->
            val newPath = buildNewImageDir(currentImage, ImagePaths.TRASH_DIR.name)
            newPath.parentFile.mkdirs()
            if(currentImage.exists()) {
                log.info { "Move image $currentImage to $newPath" }
                if(newPath.exists()) {
                    log.warn { "$newPath already exists. overwriting..." }
                }
                Files.move(currentImage.toPath(), newPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                NewImageEventBus.emitEvent(newPath)
                images.remove(currentImage)
            } else {
                log.warn { "Image $currentImage not exists" }
            }
        }
        currentImage = images.randomOrNull()
    }

    private fun buildNewImageDir(img: File, dir: String): File {
        val root = File("images")
        var x = img
        val path = mutableListOf<String>()
        while (x != root) {
            path.add(0, x.name)
            x = x.parentFile
        }
        check(path[0] == ImagePaths.DOWNLOAD_DIR.name)
        path[0] = dir
        while (path.isNotEmpty()) {
            x = File(x, path.removeFirst())
        }
        return x
    }
}