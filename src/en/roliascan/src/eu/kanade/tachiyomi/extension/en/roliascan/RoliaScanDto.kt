package eu.kanade.tachiyomi.extension.en.roliascan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PopularWrapper(
    @SerialName("most_viewed_series")
    val mangas: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val title: String,
    private val url: String,
    private val image: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = image
        url = this@MangaDto.url
    }
}
