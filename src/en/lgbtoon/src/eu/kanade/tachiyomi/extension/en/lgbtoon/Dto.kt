package eu.kanade.tachiyomi.extension.en.lgbtoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class TitlesDto(val data: TitleListDto)

@Serializable
class TitleListDto(
    val items: List<TitleItemDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class TitleItemDto(
    private val href: String,
    private val title: String,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = href
        title = this@TitleItemDto.title
        thumbnail_url = cover
    }
}

@Serializable
class TitleDto(
    val url: String,
    private val title: String,
    private val altTitles: List<String> = emptyList(),
    private val poster: String? = null,
    private val typeLabel: String? = null,
    private val status: String? = null,
    private val genres: List<NameDto> = emptyList(),
    private val authors: List<NameDto> = emptyList(),
    private val artists: List<NameDto> = emptyList(),
    private val synopsisHtml: String? = null,
    val chapters: List<ChapterDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = this@TitleDto.url
        title = this@TitleDto.title
        thumbnail_url = poster
        author = authors.joinToString { it.title }.ifEmpty { null }
        artist = artists.joinToString { it.title }.ifEmpty { null }
        genre = (listOfNotNull(typeLabel) + genres.map { it.title }).distinct().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(synopsisHtml?.let { Jsoup.parseBodyFragment(it).body() }, keepNewlines = true)
            .altTitles(altTitles)
            .build()
        status = when (this@TitleDto.status) {
            "releasing" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            "on_hiatus" -> SManga.ON_HIATUS
            "discontinued" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class NameDto(val title: String)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Double,
    private val name: String? = null,
    private val source: String? = null,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/chapter/$id"
        // Names are "Ch.39", "Chapter 35: [Uncensored]" or a group's label such as "SCAN"; the site shows only the number
        val subtitle = this@ChapterDto.name?.substringAfter(':', "")?.trim().orEmpty()
        val chapter = "Chapter ${number.toString().removeSuffix(".0")}"
        name = if (subtitle.isEmpty()) chapter else "$chapter: $subtitle"
        chapter_number = number.toFloat()
        scanlator = source
        // Ids are MongoDB ObjectIds, which begin with their creation time: the date the site shows
        date_upload = id.take(8).toLong(16) * 1000
    }
}

@Serializable
class ReaderDto(val initialPages: List<String>)
