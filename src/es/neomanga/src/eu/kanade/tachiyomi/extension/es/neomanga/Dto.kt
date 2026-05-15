package eu.kanade.tachiyomi.extension.es.neomanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class InitialMangasDto(
    val initialMangas: List<MangaDto> = emptyList(),
)

@Serializable
class MangaDto(
    val title: String,
    private val slug: String,
    private val synopsis: String? = null,
    @SerialName("cover_image_url") private val coverImageUrl: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@MangaDto.title
        this.url = slug
        this.thumbnail_url = wrapCoverUrl(coverImageUrl, baseUrl)
        this.description = synopsis
        this.genre = genres.joinToString(", ")
        this.status = when (this@MangaDto.status) {
            "en_emision" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "pausado" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class MangaDetailsDataDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    @SerialName("chapter_number") val chapterNumber: Float,
    val title: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
class ChapterPageDataDto(
    val chapter: ChapterPageDto? = null,
)

@Serializable
class ChapterPageDto(
    @SerialName("pages_urls") val pagesUrls: List<String> = emptyList(),
)

@Serializable
class MangadexPagesDto(
    val pages: List<String> = emptyList(),
)

private fun wrapCoverUrl(url: String?, baseUrl: String): String? {
    if (url == null) return null
    if (url.contains("/_next/image")) return url
    if (url.startsWith("/")) return url
    val encoded = java.net.URLEncoder.encode(url, "UTF-8").replace("+", "%20")
    return "$baseUrl/_next/image?url=$encoded&w=640&q=75"
}
