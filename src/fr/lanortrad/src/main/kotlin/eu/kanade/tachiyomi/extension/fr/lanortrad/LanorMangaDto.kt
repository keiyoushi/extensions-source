package eu.kanade.tachiyomi.extension.fr.lanortrad

import kotlinx.serialization.Serializable

@Serializable
data class LanorMangaDto(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val genres: List<String> = emptyList(),
    val status: String = "",
    val description: String = "",
    val image: String = "",
    val coverImage: String = "",
)
