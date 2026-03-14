package eu.kanade.tachiyomi.extension.all.rokuhentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResult(
    @SerialName("manga-ids") val mangaIds: Array<String>,
    @SerialName("manga-cards") val mangaCards: Array<String>,
    val prev: String,
    val next: String,
)
