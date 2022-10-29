package io.spixy.imageaggregator

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import io.spixy.imageaggregator.generated.FetchImagesByTag
import io.spixy.imageaggregator.generated.enums.AttributeType
import io.spixy.imageaggregator.generated.enums.ImageType
import io.spixy.imageaggregator.generated.fetchimagesbytag.Attribute
import io.spixy.imageaggregator.generated.fetchimagesbytag.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class JoyreactorScrapper(private val config: Joyreactor) {
    private val okHttpClient = OkHttpClient.Builder().build()
    private val regex = Regex("[ ./?#]|\\s")
    private val knownDigests = File("images/joyreactor").walk()
        .filter { it.isFile }
        .map { DigestUtils(MessageDigestAlgorithms.MD5).digestAsHex(it) }
        .toSet()

    private val client = GraphQLWebClient("https://api.joyreactor.cc/graphql")

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "JoyreactorScrapper started" }
        while (true) {
            config.tags.forEach { tag ->
                fetchImagesByTag(tag)
                delay(30.seconds)
            }
            delay(1.hours)
        }
    }

    private suspend fun fetchImagesByTag(tag: String) {
        log.info { "fetching images by tag $tag" }
        client.execute(FetchImagesByTag(FetchImagesByTag.Variables(tag))).data?.tag?.postPager?.posts?.forEach { post ->
            post.attributes
                .filter { it.image.type == ImageType.JPEG }
                .forEach { attribute ->
                    if(attribute.type == AttributeType.PICTURE) {
                        val fileName = buildFilename(post, attribute)

                        RandomQueue.add {
                            val url1 = "https://img10.joyreactor.cc/pics/post/full/$fileName"
                            val url2 = "https://img10.joyreactor.cc/pics/post/$fileName"
                            var bytes = download(url1)
                            if(bytes == null) {
                                bytes = download(url2)
                            }
                            if(bytes != null) {
                                val digest = DigestUtils(MessageDigestAlgorithms.MD5).digestAsHex(bytes)
                                if(!knownDigests.contains(digest)) {
                                    File("images/joyreactor/${escapeTag(tag)}/$fileName").let { file ->
                                        val dir = file.parentFile
                                        if(!dir.exists()) {
                                            dir.mkdirs()
                                        }
                                        file.writeBytes(bytes)
                                        log.info { "$file saved" }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun buildFilename(post: Post, attribute: Attribute): String {
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

    private fun download(url: String): ByteArray? {
        log.info { "download $url" }
        val call = okHttpClient.newCall(
            Request.Builder().url(url).build()
        )

        call.execute().use {
            if(it.headers["Content-type"]?.startsWith("image") == true) {
                return it.body?.bytes() ?: throw RuntimeException("no bytes in $url")
            } else {
                return null
            }
        }
    }
}