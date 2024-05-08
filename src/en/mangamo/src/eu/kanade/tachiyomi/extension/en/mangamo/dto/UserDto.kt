package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
class UserDto(
    val isSubscribed: Boolean? = null,
)
