package io.spixy.imageaggregator

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import java.io.File
import kotlin.time.Duration.Companion.hours

private val log = KotlinLogging.logger {}

class RedditScrapper(private val config: Reddit) {
    private val client = OkHttpClient.Builder()
        .authenticator(Authenticator { _, response ->
            if(response.request.header("Authorization") != null) {
                return@Authenticator null
            }

            log.info { "Authenticating for response: $response" }
            log.info { "Challenges: ${response.challenges()}" }

            val credentials = Credentials.basic(config.appCredentials.username, config.appCredentials.password)
            return@Authenticator response.request.newBuilder().header("Authorization", credentials)
                .build()
        })
        .build()
    private val gson = Gson()
    var saved = 0
        private set
    var exists = 0
        private set

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "RedditScrapper started" }
        while (true) {
            val token = getToken()
            log.info { "Reddit token aquired" }
            config.subreddits.forEach { subreddit ->
                scrapSubreddit(subreddit, token)
            }
            delay(1.hours)
        }
    }

    fun getToken(): String {
        val call = client.newCall(
            Request.Builder()
                .url("https://www.reddit.com/api/v1/access_token")
                .post(
                    FormBody.Builder()
                    .add("grant_type", "password")
                    .add("username", config.userCredentials.username)
                    .add("password", config.userCredentials.password)
                    .build())
                .build()
        )

        call.execute().use { response->
            check(response.code == 200) { "https://www.reddit.com/api/v1/access_token response code is not 200" }
            val json = response.body?.string()

            val token = gson.fromJson(json, Token::class.java)

            return token.access_token
        }
    }

    private fun scrapSubscribedSubreddits(token: String, after: String? = null) {
        val url = if(after != null) {
            "https://oauth.reddit.com/subreddits/mine/subscriber?limit=100&after=$after"
        } else {
            "https://oauth.reddit.com/subreddits/mine/subscriber?limit=100"
        }

        val call = client.newCall(
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${token}")
                .build()
        )

        call.execute().use { response ->
            response.body?.string()?.let { body ->
                val subreddits = parseSubreddits(body)
                    .filter { it.url.split("/")[1] == "r" }

                subreddits.forEach {
                    scrapSubreddit(it.url.split("/")[2], token)
                }

                subreddits.lastOrNull()?.let { lastSubreddit ->
                    scrapSubscribedSubreddits(lastSubreddit.name)
                }
            }
        }
    }

    private fun scrapSubreddit(subreddit: String, token: String) {
        log.info { "fetching images from subreddit $subreddit" }
        val call = client.newCall(
            Request.Builder()
                .url("https://oauth.reddit.com/r/$subreddit/top?limit=100&t=week")
                .addHeader("Authorization", "Bearer ${token}")
                .build()
        )

        call.execute().use { response ->
            response.body?.string()?.let { body ->
                saveRedditImages(body, 40)
            }
        }
    }

    private fun parseSubreddits(json: String): List<Data> {
        val obj = gson.fromJson(json, JsonObject::class.java)
        return obj.getAsJsonObject("data")
            .getAsJsonArray("children")
            .map { post ->
                gson.fromJson(
                    post.asJsonObject
                        .getAsJsonObject("data"), Data::class.java
                )
            }
    }

    private fun saveRedditImages(json: String, limit: Int) {
        val obj = gson.fromJson(json, JsonObject::class.java)
        obj.getAsJsonObject("data")
            .getAsJsonArray("children")
            .map { post ->
                gson.fromJson(
                    post.asJsonObject
                        .getAsJsonObject("data"), Data::class.java
                )
            }
            .filter { it.url.endsWith(".jpg") }
            .take(limit)
            .forEach {
                RandomQueue.add {
                    val bytes = download(it.url)
                    val digest = DigestUtils(MessageDigestAlgorithms.MD5).digestAsHex(bytes)
                    File("images/reddit/${it.subreddit}/${it.author}_${digest.take(12)}.jpg").let { file ->
                        if(!file.exists()) {
                            val dir = file.parentFile
                            if(!dir.exists()) {
                                dir.mkdirs()
                            }
                            file.writeBytes(bytes)
                            log.info { "${it.subreddit} ${it.author} ${digest.take(12)} saved" }
                            saved++
                        } else {
                            exists++
                        }
                    }
                }
            }
    }

    private fun download(url: String): ByteArray {
        log.info { "download $url" }
        val call = client.newCall(
            Request.Builder().url(url).build()
        )

        call.execute().use {
            return it.body?.bytes() ?: throw RuntimeException("no bytes in $url")
        }
    }

    class Data {
        var url: String = ""
        var name: String = ""
        var author: String = ""
        var subreddit: String = ""
    }

    class Token {
        var access_token: String = ""
    }
}