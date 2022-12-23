package io.spixy.imageaggregator.web

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.mustache.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.spixy.imageaggregator.*
import io.spixy.imageaggregator.scraper.TelegramScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File
import java.lang.StringBuilder
import kotlin.math.roundToLong

private val logger = KotlinLogging.logger {}

class WebUIController(private val config: Config.WebUI, val telegramScraper: TelegramScraper? = null) {
    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        val imageScreenOutService = ImageScreenOutService(coroutineScope)
        val imagesBattleService = ImagesBattleService(coroutineScope)

        embeddedServer(Netty, port = config.port) {
            install(Mustache) {
                mustacheFactory = DefaultMustacheFactory("templates")
            }

            routing {
                get("/") {
                    call.respond(render("index.hbs"))
                }

                get("/screenOut") {
                    try {
                        val content = imageScreenOutService.getCurrent()?.let { currentImage ->
                            val model = mapOf(
                                "img" to Img("/${currentImage.toPath()}")
                            )
                            render("screen_out.hbs", model)
                        } ?: renderMessage("No Images")
                        call.respond(content)
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/screenOut/pass") {
                    try {
                        imageScreenOutService.passAndGoNext()
                        call.respondRedirect("/screenOut")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/screenOut/delete") {
                    try {
                        imageScreenOutService.delete()
                        call.respondRedirect("/screenOut")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/battle") {
                    try {
                        val content = imagesBattleService.getCurrent()?.let { current ->
                            val model = mapOf(
                                "left" to Img("/${current.left.file.toPath()}"),
                                "right" to Img("/${current.right.file.toPath()}")
                            )
                            render("battle.hbs", model)
                        } ?: renderMessage("No Images")
                        call.respond(content)
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/battle/leftWin") {
                    try {
                        imagesBattleService.leftBetter()
                        call.respondRedirect("/battle")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/battle/rightWin") {
                    try {
                        imagesBattleService.rightBetter()
                        call.respondRedirect("/battle")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/battle/skip") {
                    try {
                        imagesBattleService.skip()
                        call.respondRedirect("/battle")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/top") {
                    try {
                        val images = imagesBattleService.getTop(50)
                        val model = mapOf(
                            "images" to images.map { Img("/${it.file.toPath()}", it.rating.roundToLong()) }
                        )
                        call.respond(render("top.hbs", model))
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/similars") {
                    try {
                        val current = ImageSimilarityService.getCurrent()
                        if(current == null) {
                            call.respond(renderMessage("no similar images found"))
                            return@get
                        }
                        val model = mapOf(
                            "dist" to current.dist,
                            "left" to Img("/${current.left.file.toPath()}",
                                width = current.left.width,
                                height =  current.left.height,
                                size = formatSize(current.left.size)),
                            "right" to Img("/${current.right.file.toPath()}",
                                width = current.right.width,
                                height =  current.right.height,
                                size = formatSize(current.right.size)),
                            "treshold" to ImageSimilarityService.similarityTreshold
                        )
                        call.respond(render("similars.hbs", model))
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/similars/deleteLeft") {
                    try {
                        ImageSimilarityService.deleteLeftAndNext()
                        call.respondRedirect("/similars")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/similars/deleteRight") {
                    try {
                        ImageSimilarityService.deleteRightAndNext()
                        call.respondRedirect("/similars")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/similars/skip") {
                    try {
                        ImageSimilarityService.skipAndNext()
                        call.respondRedirect("/similars")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/similars/deleteBoth") {
                    try {
                        ImageSimilarityService.deleteBothAndNext()
                        call.respondRedirect("/similars")
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                get("/metrics") {
                    try {
                        var redditImagesNotViewed = 0
                        var joyreactorImagesNotViewed = 0
                        var vkImagesNotViewed = 0
                        var totalImagesNotViewed = 0

                        var redditImagesPass = 0
                        var joyreactorImagesPass = 0
                        var vkImagesPass = 0
                        var totalImagesPass = 0

                        var redditImagesTrash = 0
                        var joyreactorImagesTrash = 0
                        var vkImagesTrash = 0
                        var totalImagesTrash = 0

                        var redditImagesCount = 0
                        var joyreactorImagesCount = 0
                        var vkImagesCount = 0
                        var totalImagesCount = 0

                        var redditImagesSize = 0L
                        var joyreactorImagesSize = 0L
                        var vkImagesSize = 0L
                        var totalImagesSize = 0L

                        val imageSizes = mutableListOf<Long>()

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
                                if(it.isChildOf(ImagePaths.VK_DOWNLOAD_DIR)) {
                                    vkImagesCount += 1
                                    vkImagesNotViewed += 1
                                    vkImagesSize += bytesLength
                                }
                                if(it.isChildOf(ImagePaths.VK_PASS_DIR)) {
                                    vkImagesCount += 1
                                    vkImagesPass += 1
                                    vkImagesSize += bytesLength
                                }
                                if(it.isChildOf(ImagePaths.VK_TRASH_DIR)) {
                                    vkImagesCount += 1
                                    vkImagesTrash += 1
                                    vkImagesSize += bytesLength
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
                                imageSizes.add(bytesLength)
                            }

                        val bytesPerImage = totalImagesSize / totalImagesCount
                        val midianImageSize = imageSizes.apply { sort() }[imageSizes.size / 2]

                        val content = StringBuilder()
                        content.appendLine("Total images not viewed: $totalImagesNotViewed")
                        content.appendLine("Total images screen out (pass / deleted): $totalImagesPass / $totalImagesTrash")
                        content.appendLine()
                        content.appendLine()
                        content.appendLine("Reddit images count: $redditImagesCount")
                        content.appendLine("Joyreactor images count: $joyreactorImagesCount")
                        content.appendLine("VK images count: $vkImagesCount")
                        content.appendLine("Total images count: $totalImagesCount")
                        content.appendLine()
                        content.appendLine("Reddit images size: ${formatSize(redditImagesSize)}")
                        content.appendLine("Joyreactor images size: ${formatSize(joyreactorImagesSize)}")
                        content.appendLine("VK images size: ${formatSize(vkImagesSize)}")
                        content.appendLine("Total images size: ${formatSize(totalImagesSize)}")
                        content.appendLine()
                        content.appendLine("Average image size: ${formatSize(bytesPerImage)}")
                        content.appendLine("Median image size: ${formatSize(midianImageSize)}")
                        content.appendLine()
                        content.appendLine()
                        if(telegramScraper != null) {
                            content.appendLine("Telegram metrics:")
                            telegramScraper.metrics().forEach {
                                content.appendLine(it)
                            }
                        }
                        call.respond(content.toString())
                    } catch (e: Exception) {
                        logger.error(e) { }
                    }
                }

                static("/images") {
                    staticRootFolder = File("images")
                    files(".")
                }
            }
        }.start(wait = false)

        logger.info { "WebUI server started".paintGreen() }
    }

    private fun renderMessage(msg: String): MustacheContent {
        return render("message.hbs", mapOf("message" to msg))
    }

    private fun render(template: String, model: Map<String, Any?>): MustacheContent {
        return MustacheContent(template, model)
    }

    private fun render(template: String): MustacheContent {
        return MustacheContent(template, mapOf("" to ""))
    }

    data class Img(val src: String, val rating: Long = 0, val width: Int = 0, val height: Int = 0, val size: String = "")
}