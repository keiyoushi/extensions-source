package eu.kanade.tachiyomi.extension.en.mangayi

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchRequestDto(
    private val p: Int? = null,
    private val s: String? = null,
    private val t: Int? = null,
)

@Serializable
class SearchResponseDto(
    val results: List<MangaDto>,
    val total: Int,
)

@Serializable
class MangaDto(
    @SerialName("i") private val slug: String,
    @SerialName("t") private val title: String,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        this.title = this@MangaDto.title
        thumbnail_url = "https://scp.keterfoundation.com/cover/$slug.jpg"
    }
}
