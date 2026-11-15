package eu.kanade.tachiyomi.extension.all.rokuhentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResult(
    @SerialName("manga-cards") val mangaCards: List<String>,
)
