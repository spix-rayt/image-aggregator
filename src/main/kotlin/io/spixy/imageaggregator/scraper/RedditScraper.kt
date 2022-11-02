package io.spixy.imageaggregator.scraper

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import io.spixy.imageaggregator.*
import kotlinx.coroutines.*
import mu.KotlinLogging
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}
private val allowedFileExtensions = setOf("jpg", "jpeg")

class RedditScraper(private val config: Config.Reddit): Scraper() {
    private val httpClient = OkHttpClient.Builder()
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

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "RedditScrapper started".paintGreen() }

        while (true) {
            val token = getToken()
            log.info { "Reddit token aquired" }
            config.subreddits.shuffled().forEach { subreddit ->
                try {
                    scrapSubreddit(subreddit, token)
                    delay(5.seconds)
                } catch (e: UnknownHostException) {
                    log.error(e) {  }
                    delay(1.minutes)
                }
            }
            delay(1.hours)
        }
    }

    private fun getToken(): String {
        val call = httpClient.newCall(
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

    private fun fetchSubscribedSubreddits(token: String, after: String? = null): List<String> {
        val url = if(after != null) {
            "https://oauth.reddit.com/subreddits/mine/subscriber?limit=100&after=$after"
        } else {
            "https://oauth.reddit.com/subreddits/mine/subscriber?limit=100"
        }

        val call = httpClient.newCall(
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
        )

        return call.execute().use { response ->
            response.body?.string()?.let { body ->
                parseSubreddits(body)
                    .filter { it.urlOverriddenByDest.split("/")[1] == "r" }
                    .map { it.urlOverriddenByDest.split("/")[2] }
            } ?: emptyList()
        }
    }

    private fun scrapSubreddit(subreddit: String, token: String) {
        log.info { "fetching images from subreddit $subreddit" }
        val call = httpClient.newCall(
            Request.Builder()
                .url("https://oauth.reddit.com/r/$subreddit/top?limit=100&t=week")
                .addHeader("Authorization", "Bearer $token")
                .build()
        )

        call.execute().use { response ->
            response.body?.string()?.let { json ->
                saveRedditImages(json, 40)
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
        if(obj.has("error")) {
            log.warn { "Subreddit error: $json".paintRed() }
            return
        }
        obj.getAsJsonObject("data")
            .getAsJsonArray("children")
            .map { post ->
                gson.fromJson(
                    post.asJsonObject
                        .getAsJsonObject("data"), Data::class.java
                )
            }
            .filter { data -> allowedFileExtensions.any { ext -> data.urlOverriddenByDest.endsWith(ext) } }
            .take(limit)
            .forEach {
                val fileName = it.urlOverriddenByDest.split("/").last()
                val file = File("images/download/reddit/${it.subreddit}/${it.author}_$fileName")
                val digest = "${it.subreddit}/${it.author} $fileName".md5()
                if(isUnknownHash(digest)) {
                    RunnableRandomQueue.run {
                        val bytes = httpClient.downloadImage(it.urlOverriddenByDest)
                        if(bytes != null) {
                            val fileBytesHash = bytes.md5()
                            if (isUnknownHash(fileBytesHash)) {
                                val dir = file.parentFile
                                if (!dir.exists()) {
                                    dir.mkdirs()
                                }
                                file.writeBytes(bytes)
                                ImageChangedEventBus.emitEvent(file)
                                registerHash(fileBytesHash, digest)
                                log.info { "$file saved".paintGreen() }
                            }
                        }
                    }
                }
            }
    }

    class Data {
        @SerializedName("url_overridden_by_dest")
        var urlOverriddenByDest: String = ""
        var name: String = ""
        var author: String = ""
        var subreddit: String = ""
    }

    class Token {
        var access_token: String = ""
    }
}