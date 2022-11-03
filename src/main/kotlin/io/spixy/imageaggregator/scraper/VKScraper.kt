package io.spixy.imageaggregator.scraper

import com.google.gson.Gson
import io.spixy.imageaggregator.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class VKScraper(private val config: Config.VK) : Scraper() {
    private val httpClient = OkHttpClient.Builder().build()

    private val gson = Gson()

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "VKScrapper started".paintGreen() }

        while (true) {
            config.clubs.shuffled().forEach { club ->
                try {
                    scrapClubWall(club)
                    delay(5.seconds)
                } catch (e: UnknownHostException) {
                    log.error(e) {  }
                    delay(1.minutes)
                }
            }
            delay(1.hours)
        }
    }

    private fun scrapClubWall(club: String) {
        log.info { "fetching images from vk club $club" }
        val url = "https://api.vk.com/method/wall.get".toHttpUrl().newBuilder()
            .addQueryParameter("access_token", config.accessToken)
            .addQueryParameter("domain", club)
            .addQueryParameter("count", "100")
            .addQueryParameter("v", "5.131")
            .build()

        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            response.body?.string()?.let { body ->
                gson.fromJson(body, GetWallResponse::class.java).response?.items?.forEach { item ->
                    item.attachments?.forEach { attachment ->
                        processAttachment(attachment, club)
                    }
                }
            }
        }
    }

    private fun processAttachment(attachment: Attachment, club: String) {
        if (attachment.type == "photo") {
            if (attachment.photo?.sizes != null) {
                val photo = getBiggestPhoto(attachment.photo.sizes)
                val digest = attachment.photo.let {
                    "${it.id} ${it.owner_id} ${photo.width} ${photo.height}".md5()
                }
                if (isUnknownHash(digest)) {
                    RunnableRandomQueue.run {
                        val bytes = httpClient.downloadImage(photo.url)
                        if (bytes != null) {
                            val fileBytesHash = bytes.md5()
                            val fileName = photo.url.split("?").first().split("/").last()
                            val file = File("images/download/vk/$club/$fileName")
                            writeFile(file, bytes, fileBytesHash, digest)
                        }
                    }
                }
            }
        }
    }

    private fun getBiggestPhoto(sizes: List<PhotoSize>): PhotoSize {
        return sizes.maxBy { it.width * it.height }
    }

    data class GetWallResponse(
        val response: GetWallResponseInternal? = null
    )

    data class GetWallResponseInternal(
        val count: Int = 0,
        val items: List<WallItems>? = null
    )

    data class WallItems(
        val id: Int = 0,
        val owner_id: Int = 0,
        val attachments: List<Attachment>? = null
    )

    data class Attachment(
        val type: String = "",
        val photo: Photo? = null
    )

    data class Photo(
        val id: Int = 0,
        val owner_id: Int = 0,
        val sizes: List<PhotoSize>? = null
    )

    data class PhotoSize(
        val url: String = "",
        val width: Int = 0,
        val height: Int = 0
    )
}