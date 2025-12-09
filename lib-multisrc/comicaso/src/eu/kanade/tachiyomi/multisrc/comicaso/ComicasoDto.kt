package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class MangaListResponse(
    val items: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val thumb: String,
    private val status: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/v2/manga/$slug/"
        title = this@MangaDto.title
        thumbnail_url = thumb
        status = when (this@MangaDto.status) {
            "on-going" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}
