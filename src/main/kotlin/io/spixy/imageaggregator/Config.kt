package io.spixy.imageaggregator

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val vk: VK? = null,
    val joyreactor: Joyreactor? = null,
    val reddit: Reddit? = null,
    val telegram: Telegram? = null,
    val webUi: WebUI = WebUI()
) {
    @Serializable
    data class Joyreactor (
        val tags: List<String>
    )

    @Serializable
    data class Reddit(
        val appCredentials: BasicCredentials,
        val userCredentials: BasicCredentials,
        val subreddits: List<String>
    )

    @Serializable
    data class VK(
        val accessToken: String,
        val clubs: List<String>
    )

    @Serializable
    data class Telegram(
        val phone: String,
        val chatIds: List<Long>
    )

    @Serializable
    data class BasicCredentials(
        val username: String,
        val password: String
    )

    @Serializable
    data class WebUI(
        val port: Int = 8080
    )
}