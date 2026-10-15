package eu.kanade.tachiyomi.multisrc.loneseal

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class SearchResponseDto(
    val data: List<MangaDto>,
    @SerialName("total_pages") private val totalPages: Int,
) {
    fun toMangasPage(page: Int, urlLayout: UrlLayout) = MangasPage(
        data.map { it.toSManga(urlLayout) },
        page < totalPages,
    )
}

@Serializable
class MangaDto(
    private val title: String,
    val slug: String,
    @SerialName("poster_image_url") private val posterImageUrl: String? = null,
) {
    fun toSManga(urlLayout: UrlLayout) = SManga.create().apply {
        url = urlLayout.mangaUrl(slug)
        title = this@MangaDto.title.trim()
        thumbnail_url = posterImageUrl
    }
}

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class SeriesDetailDto(
    private val title: String,
    val slug: String,
    val synopsis: String? = null,
    @SerialName("poster_image_url") private val posterImageUrl: String? = null,
    @SerialName("comic_status") private val comicStatus: String? = null,
    @SerialName("author_name") private val authorName: String? = null,
    @SerialName("artist_name") private val artistName: String? = null,
    @SerialName("primary_genre") private val primaryGenre: String? = null,
    val genres: List<GenreDto>,
    val units: List<ChapterDto>,
) {
    fun toSManga(urlLayout: UrlLayout) = SManga.create().apply {
        url = urlLayout.mangaUrl(slug)
        title = this@SeriesDetailDto.title.trim()
        thumbnail_url = posterImageUrl
        author = authorName
        artist = artistName
        description = synopsis?.let { it.toMarkdownDescription() }
        genre = buildList {
            if (primaryGenre != null) add(primaryGenre)
            addAll(genres.map { it.name })
        }.distinct().joinToString().ifEmpty { null }
        status = comicStatus.parseStatus()
    }
}

@Serializable
class ChapterDto(
    private val slug: String,
    private val number: String,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(seriesSlug: String, urlLayout: UrlLayout, includeChapterTitle: Boolean) = SChapter.create().apply {
        url = urlLayout.chapterUrl(seriesSlug, slug)
        name = buildString {
            append("Chapter ${formatChapterNumber(number)}")
            if (includeChapterTitle && title != null) append(" - $title")
        }
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterPagesResponseDto(
    val chapter: ChapterPagesDto = ChapterPagesDto(),
)

@Serializable
class ChapterPagesDto(
    @SerialName("login_required") val loginRequired: Boolean? = null,
    @SerialName("password_required") val passwordRequired: Boolean = false,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
class HomeSectionsDto(
    @SerialName("latest_comic_updates") val latestComicUpdates: List<LatestComicUpdateDto> = emptyList(),
)

@Serializable
class LatestComicUpdateDto(
    @SerialName("series_title") private val seriesTitle: String,
    @SerialName("series_slug") private val seriesSlug: String,
    @SerialName("poster_image_url") private val posterImageUrl: String? = null,
) {
    fun toSManga(urlLayout: UrlLayout) = SManga.create().apply {
        url = urlLayout.mangaUrl(seriesSlug)
        title = seriesTitle.trim()
        thumbnail_url = posterImageUrl
    }
}

private val synopsisAnchorRegex = """<a\s+href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""".toRegex(RegexOption.IGNORE_CASE)

private fun String.toMarkdownDescription(): String = replace(synopsisAnchorRegex) { "[${it.groupValues[2].trim()}](${it.groupValues[1].trim()})" }
    .replace("<strong>", "**")
    .replace("</strong>", "**")
    .replace("<p>", "\n\n")
    .replace("</p>", "\n\n")
    .replace("<", "\\<")
    .replace(">", "\\>")

private fun formatChapterNumber(number: String): String = number.removeSuffix(".00")

private fun String?.parseStatus() = when (this?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    else -> SManga.UNKNOWN
}
