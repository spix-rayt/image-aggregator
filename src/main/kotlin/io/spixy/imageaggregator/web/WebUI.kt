package io.spixy.imageaggregator.web

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.TemplateFunction
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.mustache.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.spixy.imageaggregator.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import okhttp3.internal.wait
import org.apache.commons.io.FileUtils
import java.io.File

private val log = KotlinLogging.logger {}

class WebUI(private val config: Config.WebUI) {
    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        val imagePass = ImagePass(coroutineScope)
        val imageComparisons = ImageComparisons(coroutineScope)

        embeddedServer(Netty, port = config.port) {
            install(Mustache) {
                mustacheFactory = DefaultMustacheFactory("templates")
            }

            routing {
                get("/") {
                    call.respond(render("index.hbs"))
                }

                get("/downloadsFilter") {
                    val currentImage = imagePass.getCurrent()
                    try {
                        val content = currentImage?.let { currentImage ->
                            val model = mapOf(
                                "img" to Img("/${currentImage.toPath()}")
                            )
                            MustacheContent("screen_out.hbs", model)
                        } ?: MustacheContent("no_images.hbs", null)
                        call.respond(content)
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/move/passAndGoNext") {
                    try {
                        imagePass.passAndGoNext()
                        call.respondRedirect("/downloadsFilter")
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/move/delete") {
                    try {
                        imagePass.delete()
                        call.respondRedirect("/downloadsFilter")
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/compare") {
                    try {
                        val content = imageComparisons.getCurrent()?.let { current ->
                            val model = mapOf(
                                "left" to Img("/${current.left.file.toPath()}"),
                                "right" to Img("/${current.right.file.toPath()}")
                            )
                            MustacheContent("compare.hbs", model)
                        } ?: MustacheContent("no_images.hbs", null)
                        call.respond(content)
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/comparisonResult/left") {
                    try {
                        imageComparisons.leftBetter()
                        call.respondRedirect("/compare")
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/comparisonResult/right") {
                    try {
                        imageComparisons.rightBetter()
                        call.respondRedirect("/compare")
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/comparisonResult/skip") {
                    try {
                        imageComparisons.skip()
                        call.respondRedirect("/compare")
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/top") {
                    try {
                        val images = imageComparisons.getTop(20)
                        val model = mapOf(
                            "images" to images.map { Img("/${it.file.toPath()}", it.rating) }
                        )
                        call.respond(MustacheContent("top.hbs", model))
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/metrics") {
                    try {
                        var redditImagesNotViewed = 0
                        var joyreactorImagesNotViewed = 0
                        var totalImagesNotViewed = 0

                        var redditImagesPass = 0
                        var joyreactorImagesPass = 0
                        var totalImagesPass = 0

                        var redditImagesTrash = 0
                        var joyreactorImagesTrash = 0
                        var totalImagesTrash = 0

                        var redditImagesCount = 0
                        var joyreactorImagesCount = 0
                        var totalImagesCount = 0

                        var redditImagesSize = 0L
                        var joyreactorImagesSize = 0L
                        var totalImagesSize = 0L

                        ImagePaths.IMAGES_DIR.toFile().walk()
                            .filter { it.isFile }
                            .filter { it.hasImageExtension() }
                            .forEach {
                                val bytesLength = it.length()
                                if(it.isChildOf(ImagePaths.REDDIT_DOWNLOAD_DIR)) {
                                    redditImagesCount += 1
                                    redditImagesNotViewed += 1
                                    redditImagesSize += bytesLength
                                }
                                if(it.isChildOf(ImagePaths.REDDIT_PASS_DIR)) {
                                    redditImagesCount += 1
                                    redditImagesPass += 1
                                    redditImagesSize += bytesLength
                                }
                                if(it.isChildOf(ImagePaths.REDDIT_TRASH_DIR)) {
                                    redditImagesCount += 1
                                    redditImagesTrash += 1
                                    redditImagesSize += bytesLength
                                }
                                if(it.isChildOf(ImagePaths.JOYREACTOR_DOWNLOAD_DIR)) {
                                    joyreactorImagesCount += 1
                                    joyreactorImagesNotViewed += 1
                                    joyreactorImagesSize += bytesLength
                                }
                                if(it.isChildOf(ImagePaths.JOYREACTOR_PASS_DIR)) {
                                    joyreactorImagesCount += 1
                                    joyreactorImagesPass += 1
                                    joyreactorImagesSize += bytesLength
                                }
                                if(it.isChildOf(ImagePaths.JOYREACTOR_TRASH_DIR)) {
                                    joyreactorImagesCount += 1
                                    joyreactorImagesTrash += 1
                                    joyreactorImagesSize += bytesLength
                                }
                                if(it.isChildOf(ImagePaths.DOWNLOAD_DIR)) {
                                    totalImagesNotViewed += 1
                                }
                                if(it.isChildOf(ImagePaths.PASS_DIR)) {
                                    totalImagesPass += 1
                                }
                                if(it.isChildOf(ImagePaths.TRASH_DIR)) {
                                    totalImagesTrash += 1
                                }
                                totalImagesCount += 1
                                totalImagesSize += bytesLength
                            }
                        val content = """
                            Total images not viewed: $totalImagesNotViewed
                            Total images screen out (pass / deleted): $totalImagesPass / $totalImagesTrash
                            
                            
                            Reddit images count: $redditImagesCount
                            Joyreactor images count: $joyreactorImagesCount
                            Total images count: $totalImagesCount
                            
                            Reddit images size: ${formatSize(redditImagesSize)}
                            Joyreactor images size: ${formatSize(joyreactorImagesSize)}
                            Total images size: ${formatSize(totalImagesSize)}
                        """.trimIndent()
                        call.respond(content)
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                static("/images") {
                    staticRootFolder = File("images")
                    files(".")
                }
            }
        }.start(wait = false)

        log.info { "WebUI server started".paintGreen() }
    }

    private fun render(template: String, model: HashMap<String, Any?>): MustacheContent {
        return MustacheContent(template, model)
    }

    private fun render(template: String): MustacheContent {
        return MustacheContent(template, mapOf("" to ""))
    }

    data class Img(val src: String, val rating: Double = 0.0)
}