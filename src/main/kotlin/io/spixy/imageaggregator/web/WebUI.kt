package io.spixy.imageaggregator.web

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.mustache.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.spixy.imageaggregator.Config
import io.spixy.imageaggregator.paintGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
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
                    val currentImage = imagePass.getCurrent()
                    try {
                        val content = currentImage?.let { currentImage ->
                            val model = mapOf(
                                "img" to Img("/${currentImage.toPath()}")
                            )
                            MustacheContent("main.hbs", model)
                        } ?: MustacheContent("noimages.hbs", null)
                        call.respond(content)
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/move/passAndGoNext") {
                    try {
                        imagePass.passAndGoNext()
                        call.respondRedirect("/")
                    } catch (e: Exception) {
                        io.spixy.imageaggregator.web.log.error(e) { }
                    }
                }

                get("/move/delete") {
                    try {
                        imagePass.delete()
                        call.respondRedirect("/")
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
                        } ?: MustacheContent("noimages.hbs", null)
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
                        val images = imageComparisons.getTop()
                        val model = mapOf(
                            "images" to images.map { Img("/${it.file.toPath()}", it.rating) }
                        )
                        call.respond(MustacheContent("top.hbs", model))
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

    data class Img(val src: String, val rating: Double = 0.0)
}