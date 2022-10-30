package io.spixy.imageaggregator

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val joyreactor: Joyreactor? = null,
    val reddit: Reddit? = null
)

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
data class BasicCredentials(
    val username: String,
    val password: String
)