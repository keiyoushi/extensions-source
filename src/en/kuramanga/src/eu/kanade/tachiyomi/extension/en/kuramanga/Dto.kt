package eu.kanade.tachiyomi.extension.en.kuramanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val data: List<MangaDto>,
    val total: Int,
)

@Serializable
class MangaDto(
    private val title: String,
    @SerialName("cover_image_url") private val coverImageUrl: String? = null,
    @SerialName("normalized_title") private val normalizedTitle: String,
) {
    fun toSManga() = SManga.create().apply {
        this.title = this@MangaDto.title
        this.url = "/${this@MangaDto.normalizedTitle}"
        this.thumbnail_url = this@MangaDto.coverImageUrl
    }
}
