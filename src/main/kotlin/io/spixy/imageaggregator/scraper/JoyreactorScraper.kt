package io.spixy.imageaggregator.scraper

import com.expediagroup.graphql.client.serializer.defaultGraphQLSerializer
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.spixy.imageaggregator.Config
import io.spixy.imageaggregator.RunnableRandomQueue
import io.spixy.imageaggregator.generated.FetchImagesByTag
import io.spixy.imageaggregator.generated.GetCountImagesByTag
import io.spixy.imageaggregator.generated.enums.AttributeType
import io.spixy.imageaggregator.generated.enums.ImageType
import io.spixy.imageaggregator.generated.fetchimagesbytag.Attribute
import io.spixy.imageaggregator.generated.fetchimagesbytag.Post
import io.spixy.imageaggregator.md5
import io.spixy.imageaggregator.paintGreen
import kotlinx.coroutines.*
import mu.KotlinLogging
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.codec.binary.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class JoyreactorScraper(private val config: Config.Joyreactor): RestScraper() {
    private val httpClient = OkHttpClient.Builder().build()
    private val regex = Regex("[ ./?#]|\\s")
    private val serializer = defaultGraphQLSerializer()

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "JoyreactorScrapper started".paintGreen() }

        while (true) {
            try {
                config.tags.shuffled().forEach { tag ->
                    scrapTag(tag)
                    delay(5.seconds)
                }
            } catch (e: Exception) {
                log.error(e) {  }
            }
            delay(1.hours)
        }
    }

    private suspend fun scrapTag(tag: String) {
        log.info { "fetching images by tag $tag" }
        val postsCount = withContext(Dispatchers.IO) {
            execute(GetCountImagesByTag(GetCountImagesByTag.Variables(tag)))
        }.data?.tag?.postPager?.count ?: 0.also { log.warn { "Can't get count posts for tag $tag" } }

        val lastPage = (postsCount - 1) / 10 + 1

        for(page in (lastPage - 4)..lastPage)
            withContext(Dispatchers.IO) { execute(FetchImagesByTag(FetchImagesByTag.Variables(tag, page))) }
                .data
                ?.tag
                ?.postPager
                ?.posts
                ?.forEach { post ->
                    post.attributes
                        .filter { it.image.type == ImageType.JPEG }
                        .forEach { attribute ->
                            processAttribute(attribute, post, tag)
                        }
        }
    }

    private suspend fun <T : Any> execute(request: GraphQLClientRequest<T>): GraphQLClientResponse<T> {
        val rawResult = withContext(Dispatchers.IO) {
            httpClient.newCall(
                Request.Builder()
                    .url("https://api.joyreactor.cc/graphql")
                    .header("Accept", "application/json; charset=utf-8")
                    .post(serializer.serialize(request)
                        .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .build()).execute().use { response ->
                response.body?.string() ?: error("Joyreactor request returns no body")
            }
        }

        return serializer.deserialize(rawResult, request.responseType())
    }

    private fun processAttribute(attribute: Attribute, post: Post, tag: String) {
        if (attribute.type == AttributeType.PICTURE) {
            val fileName = extractFileName(post, attribute)

            if (isUnknownHash(fileName.md5())) {
                RunnableRandomQueue.run {
                    val url1 = "https://img10.joyreactor.cc/pics/post/full/$fileName"
                    val url2 = "https://img10.joyreactor.cc/pics/post/$fileName"
                    var bytes: ByteArray? = null
                    var retriesLeft = 3
                    while (retriesLeft > 0) {
                        try {
                            retriesLeft -= 1
                            bytes = httpClient.downloadImage(url1)
                            if (bytes == null) {
                                bytes = httpClient.downloadImage(url2)
                            }
                            retriesLeft = 0
                        } catch (e: IOException) {
                            log.error(e) { "download error. retriesLeft = $retriesLeft" }
                            delay(30.seconds)
                        }
                    }

                    if (bytes != null) {
                        val file = File("images/download/joyreactor/${escapeTag(tag)}/$fileName")
                        bytes = removeBottomJoyreactorLine(bytes, file.extension)
                        val fileBytesHash = bytes.md5()
                        writeFile(file, bytes, fileBytesHash, fileName.md5())
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
}