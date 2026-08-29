package eu.kanade.tachiyomi.extension.en.mangauno

import eu.kanade.tachiyomi.extension.en.mangauno.Mangauno.Companion.IMG_API_URL
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat

@Serializable
class ListResponse(
    private val data: List<MangaDto>,
) {
    fun toSMangaList(useEnglish: Boolean): List<SManga> = data.map { it.toSManga(useEnglish) }
}

@Serializable
class MangaDto(
    private val slug: String,
    @SerialName("english_title") private val englishTitle: String?,
    @SerialName("japanese_title") private val japaneseTitle: String?,
    private val title: String,
    private val cover: String?,
) {
    fun toSManga(useEnglish: Boolean) = SManga.create().apply {
        url = slug
        this.title = if (useEnglish) {
            this@MangaDto.englishTitle?.ifEmpty { null } ?: this@MangaDto.title
        } else {
            this@MangaDto.japaneseTitle?.ifEmpty { null } ?: this@MangaDto.title
        }
        thumbnail_url = cover?.let { "$IMG_API_URL$it" }
    }
}

@Serializable
class DetailsResponse(
    private val manga: MangaDetailsDto,
    private val chapters: List<ChapterDto>,
) {
    fun toSManga(useEnglish: Boolean) = manga.toSManga(useEnglish)
    fun toSChapterList(dateFormat: SimpleDateFormat) = chapters.map { it.toSChapter(dateFormat, manga.slug) }
}

@Serializable
class MangaDetailsDto(
    val slug: String,
    @SerialName("english_title") private val englishTitle: String?,
    @SerialName("japanese_title") private val japaneseTitle: String?,
    private val title: String,
    private val cover: String?,
    private val synopsis: String?,
    private val author: String?,
    private val artist: String?,
    private val genres: String?,
    private val tags: String?,
    private val status: String?,
) {
    fun toSManga(useEnglish: Boolean) = SManga.create().apply {
        url = slug
        this.title = if (useEnglish) {
            this@MangaDetailsDto.englishTitle?.ifEmpty { null } ?: this@MangaDetailsDto.title
        } else {
            this@MangaDetailsDto.japaneseTitle?.ifEmpty { null } ?: this@MangaDetailsDto.title
        }
        thumbnail_url = cover?.let { "$IMG_API_URL$it" }
        description = synopsis
        author = this@MangaDetailsDto.author?.replace(" & ", ", ")
        artist = this@MangaDetailsDto.artist?.replace(" & ", ", ")

        val parsedGenres = genres?.parseAs<List<String>>() ?: emptyList()
        val parsedTags = tags?.parseAs<List<String>>() ?: emptyList()
        genre = (parsedGenres + parsedTags).filter { it.isNotEmpty() }.joinToString()

        this.status = when (this@MangaDetailsDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("chapter_number") private val chapterNumber: String?,
    private val volume: Int?,
    private val title: String?,
    private val source: String?,
    @SerialName("published_at") private val publishedAt: String?,
) {
    fun toSChapter(dateFormat: SimpleDateFormat, mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$id"

        val chStr = chapterNumber?.toFloatOrNull()?.toString()?.removeSuffix(".0")?.let { "Ch. $it" }
        val volStr = volume?.let { "Vol. $it" }
        val unescapedTitle = title?.let { Parser.unescapeEntities(it, true) }

        val parts = listOfNotNull(chStr, volStr, unescapedTitle).filter { it.isNotEmpty() }
        name = parts.joinToString(" — ")
        if (name.isEmpty()) name = "Chapter"

        scanlator = source
        date_upload = dateFormat.tryParse(publishedAt)
    }
}

@Serializable
class PageListResponse(
    val pages: List<String>,
)

@Serializable
class FacetsDto(
    val genres: List<FacetDto> = emptyList(),
    val tags: List<FacetDto> = emptyList(),
)

@Serializable
class FacetDto(
    val name: String,
)
