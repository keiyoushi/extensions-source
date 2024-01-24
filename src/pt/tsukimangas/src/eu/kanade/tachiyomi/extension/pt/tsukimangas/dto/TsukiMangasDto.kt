package eu.kanade.tachiyomi.extension.pt.tsukimangas.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaListDto(
    val data: List<SimpleMangaDto>,
    val page: Int,
    val lastPage: Int,
)

@Serializable
data class SimpleMangaDto(
    val id: Int,
    @SerialName("url") val slug: String,
    val title: String,
    val poster: String? = null,
    val cover: String? = null,
) {
    val imagePath = "/img/imgs/${poster ?: cover ?: "nobackground.jpg"}"
    val entryPath = "/$id/$slug"
}
