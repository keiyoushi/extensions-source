package eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PopularManga(
    val id: Int,
    val title: String,
    val author: String,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String,
)
