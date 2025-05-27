package eu.kanade.tachiyomi.extension.pt.gekkouscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val CDN_URL = "https://usc1.contabostorage.com/783e4d097dbf4f83aefe59be94798c82:gekkou"

@Serializable
class MangaDto(
    val slug: String,
    val name: String,
    val artists: String,
    val author: String,
    val genres: List<String>,
    val status: String,
    val popular: Boolean,
    val summary: String,
    val chapters: List<ChapterDto>,
    private val urlCover: String,
) {
    val thumbnailUrl: String get() = getAbsoluteThumbnailUrl(urlCover)

    fun toSManga() = SManga.create().apply {
        title = name
        url = "/projeto/$slug"
        description = summary
        genre = genres.joinToString()
        artist = artists
        author = this@MangaDto.author
        initialized = true
        status = when (this@MangaDto.status.lowercase()) {
            "completo" -> SManga.COMPLETED
            "ativo" -> SManga.ONGOING
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = thumbnailUrl
    }

    fun toListSChapter(): List<SChapter> {
        return chapters.map {
            SChapter.create().apply {
                name = it.chapterNumber
                chapter_number = it.chapterNumber.toFloat()
                url = "/leitor/$slug/${it.chapterNumber}"
            }
        }
    }
}

@Serializable
class ChapterDto(
    @SerialName("chapterSlug")
    val chapterNumber: String,
)

@Serializable
class LatestMangaDto(
    @SerialName("mangaSlug")
    val slug: String,
    val name: String,
    private val urlCover: String,
) {
    val thumbnailUrl: String get() = getAbsoluteThumbnailUrl(urlCover)
}

@Serializable
class PagesDto(
    val pages: List<ImageUrl>,
) {
    @Serializable
    class ImageUrl(
        @SerialName("pageNumber")
        val index: Int,
        val url: String,
    )
}

fun getAbsoluteThumbnailUrl(urlCover: String): String {
    return when {
        urlCover.startsWith("http", ignoreCase = true) -> urlCover
        else -> "$CDN_URL/$urlCover"
    }
}
