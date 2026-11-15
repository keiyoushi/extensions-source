package eu.kanade.tachiyomi.multisrc.liliana

import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val list: List<MangaDto>,
) {
    @Serializable
    class MangaDto(
        val cover: String,
        val name: String,
        val url: String,
    )
}

@Serializable
class PageListResponseDto(
    val status: Boolean = false,
    val msg: String? = null,
    val html: String,
)
