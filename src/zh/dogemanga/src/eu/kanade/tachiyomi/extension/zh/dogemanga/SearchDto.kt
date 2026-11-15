package eu.kanade.tachiyomi.extension.zh.dogemanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchDto(
    @SerialName("manga_cards") val mangaCards: List<String>,
    val next: String? = null,
)
