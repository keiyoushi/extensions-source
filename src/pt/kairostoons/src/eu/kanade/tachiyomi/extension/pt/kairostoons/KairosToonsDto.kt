package eu.kanade.tachiyomi.extension.pt.kairostoons

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchDto(
    val results: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val title: String,
    private val url: String,
    @SerialName("cover_url")
    private val thumbnail: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = "$baseUrl$thumbnail"
        url = this@MangaDto.url
    }
}
