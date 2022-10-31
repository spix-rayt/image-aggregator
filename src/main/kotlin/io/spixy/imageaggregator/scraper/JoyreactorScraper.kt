package io.spixy.imageaggregator.scraper

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import io.spixy.imageaggregator.*
import io.spixy.imageaggregator.generated.FetchImagesByTag
import io.spixy.imageaggregator.generated.GetCountImagesByTag
import io.spixy.imageaggregator.generated.enums.AttributeType
import io.spixy.imageaggregator.generated.enums.ImageType
import io.spixy.imageaggregator.generated.fetchimagesbytag.Attribute
import io.spixy.imageaggregator.generated.fetchimagesbytag.Post
import kotlinx.coroutines.*
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.binary.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class JoyreactorScraper(private val config: Config.Joyreactor): Scraper() {
    private val okHttpClient = OkHttpClient.Builder().build()
    private val regex = Regex("[ ./?#]|\\s")

    private val client = GraphQLWebClient("https://api.joyreactor.cc/graphql")

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "JoyreactorScrapper started".paintGreen() }

        while (true) {
            config.tags.shuffled().forEach { tag ->
                fetchImagesByTag(tag)
                delay(5.seconds)
            }
            delay(1.hours)
        }
    }

    private suspend fun fetchImagesByTag(tag: String) {
        log.info { "fetching images by tag $tag" }
        val postsCount = withContext(Dispatchers.IO) {
            client.execute(GetCountImagesByTag(GetCountImagesByTag.Variables(tag)))
        }
            .data?.tag?.postPager?.count
            ?: 0.also { log.warn { "Can't get count posts for tag $tag" } }

        val lastPage = (postsCount - 1) / 10 + 1

        for(page in (lastPage - 4)..lastPage)
            withContext(Dispatchers.IO) { client.execute(FetchImagesByTag(FetchImagesByTag.Variables(tag, page))) }
                .data
                ?.tag
                ?.postPager
                ?.posts
                ?.forEach { post ->
                    post.attributes
                        .filter { it.image.type == ImageType.JPEG }
                        .forEach { attribute ->
                            if (attribute.type == AttributeType.PICTURE) {
                                val fileName = extractFileName(post, attribute)
                                val file = File("images/download/joyreactor/${escapeTag(tag)}/$fileName")

                                if(!hashes.contains(fileName.md5())) {
                                    RunnableRandomQueue.run {
                                        val url1 = "https://img10.joyreactor.cc/pics/post/full/$fileName"
                                        val url2 = "https://img10.joyreactor.cc/pics/post/$fileName"
                                        var bytes: ByteArray? = null
                                        var retriesLeft = 3
                                        while (retriesLeft > 0) {
                                            try {
                                                retriesLeft -= 1
                                                bytes = download(url1)
                                                if (bytes == null) {
                                                    bytes = download(url2)
                                                }
                                                retriesLeft = 0
                                            } catch (e: IOException) {
                                                log.error(e) { "download error. retriesLeft = $retriesLeft" }
                                                delay(30.seconds)
                                            }
                                        }

                                        if (bytes != null) {
                                            bytes = removeBottomJoyreactorLine(bytes, file.extension)
                                            val fileBytesHash = bytes.md5()
                                            if (!hashes.contains(fileBytesHash)) {
                                                val dir = file.parentFile
                                                if (!dir.exists()) {
                                                    dir.mkdirs()
                                                }
                                                file.writeBytes(bytes)
                                                ImageChangedEventBus.emitEvent(file)
                                                registerHash(fileBytesHash, fileName.md5())
                                                log.info { "$file saved".paintGreen() }
                                            }
                                        }
                                    }
                                }
                            }
                        }
        }
    }

    private fun extractFileName(post: Post, attribute: Attribute): String {
        val id = Base64.decodeBase64(attribute.id).toString(StandardCharsets.UTF_8)
            .split(":").last().toInt()
        val tags = post.tags
            .take(3)
            .joinToString("-") { escapeTag(it.name) }
            .ifEmpty { "picture" }
        val extension = attribute.image.type.name.lowercase()
        return "$tags-$id.$extension"
    }

    private fun escapeTag(tag: String): String {
        return tag.replace(regex, "-")
    }

    private fun removeBottomJoyreactorLine(imgBytes: ByteArray, format: String): ByteArray {
        ImageIO.setUseCache(false)
        val img = ImageIO.read(ByteArrayInputStream(imgBytes))
        val newImg = img.getSubimage(0, 0, img.width, img.height - 14)
        val byteArrayOutputStream = ByteArrayOutputStream()
        ImageIO.write(newImg, format, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        log.info { "download $url" }
        val call = okHttpClient.newCall(
            Request.Builder().url(url).build()
        )

        call.execute().use {
            if (it.headers["Content-type"]?.startsWith("image") == true) {
                it.body?.bytes() ?: throw RuntimeException("no bytes in $url")
            } else {
                null
            }
        }
    }
}