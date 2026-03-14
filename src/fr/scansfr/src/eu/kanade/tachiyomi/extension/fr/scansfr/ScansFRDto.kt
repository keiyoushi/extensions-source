package eu.kanade.tachiyomi.extension.fr.scansfr

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val STATIC_URL = "https://api.scansfr.com"

@Serializable
internal class MangaListDto(
    val mangas: List<MangaBriefDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
internal data class MangaBriefDto(
    val slug: String,
    val title: String,
    val cover: String,
    val chapters: Int = 0,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        this.title = this@MangaBriefDto.title
        thumbnail_url = "$STATIC_URL$cover"
    }
}

@Serializable
internal data class MangaDetailDto(
    val slug: String,
    val title: String,
    val description: String = "",
    val cover: String,
    val status: String,
    val tags: List<String> = emptyList(),
    val author: String? = null,
    val artist: String? = null,
    @SerialName("chaptersList") val chaptersList: List<ChapterBriefDto> = emptyList(),
)

@Serializable
internal data class ChapterBriefDto(
    val number: Double,
    val title: String,
    val date: String? = null,
)

@Serializable
internal data class ChapterDetailDto(
    val pageCount: Int,
)

@Serializable
internal data class TokensResponseDto(
    val tokens: List<TokenDto>,
)

@Serializable
internal data class TokenDto(
    val pageNumber: Int,
    val token: String,
)
