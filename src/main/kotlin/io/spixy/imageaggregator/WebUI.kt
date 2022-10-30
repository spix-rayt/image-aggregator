package io.spixy.imageaggregator

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.mustache.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val log = KotlinLogging.logger {}

class WebUI(private val config: Config.WebUI) {
    private val downloadedDirectories = listOf(File("images/reddit"), File("images/joyreactor"))
    private val images = downloadedDirectories.asSequence()
        .flatMap { it.walk() }
        .filter { it.isFile }
        .toMutableList()
    private var currentImage: File? = null

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        embeddedServer(Netty, port = config.port) {
            install(Mustache) {
                mustacheFactory = DefaultMustacheFactory("templates")
            }

            routing {
                get("/") {
                    try {
                        if (currentImage == null) {
                            currentImage = images.randomOrNull()
                        }
                        val content = currentImage?.let { currentImage ->
                            val model = mapOf(
                                "img" to Img("/${currentImage.toPath()}")
                            )
                            MustacheContent("main.hbs", model)
                        } ?: MustacheContent("noimages.hbs", null)
                        call.respond(content)
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.log.error(e) {  }
                    }
                }

                get("/move/passAndGoNext") {
                    try {
                        currentImage?.let { currentImage ->
                            val newPath = buildNewImageDir(currentImage, "pass")
                            newPath.parentFile.mkdirs()
                            io.spixy.imageaggregator.log.info { "Move image $currentImage to $newPath" }
                            Files.move(currentImage.toPath(), newPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            images.remove(currentImage)
                        }
                        currentImage = images.randomOrNull()
                        call.respondRedirect("/")
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.log.error(e) {  }
                    }
                }

                get("/move/delete") {
                    try {
                        currentImage?.let { currentImage ->
                            val newPath = buildNewImageDir(currentImage, "trash")
                            newPath.parentFile.mkdirs()
                            io.spixy.imageaggregator.log.info { "Move image $currentImage to $newPath" }
                            Files.move(currentImage.toPath(), newPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            images.remove(currentImage)
                        }
                        currentImage = images.randomOrNull()
                        call.respondRedirect("/")
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.log.error(e) {  }
                    }
                }

                static("/images") {
                    staticRootFolder = File("images")
                    files(".")
                }
            }
        }.start(wait = false)
    }

    private fun buildNewImageDir(img: File, dir: String): File {
        val root = File("images")
        var x = img
        val path = mutableListOf<String>()
        while (x != root) {
            path.add(0, x.name)
            x = x.parentFile
        }
        path.add(0, dir)
        while (path.isNotEmpty()) {
            x = File(x, path.removeFirst())
        }
        return x
    }

    data class Img(val src: String)
}