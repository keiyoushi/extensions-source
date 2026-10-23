package eu.kanade.tachiyomi.extension.en.kaliscansorg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Genre(val name: String, val value: String)

@Serializable
class LinkedData(
    @SerialName("@type") val type: String? = null,
    val ratingValue: Double? = null,
)
