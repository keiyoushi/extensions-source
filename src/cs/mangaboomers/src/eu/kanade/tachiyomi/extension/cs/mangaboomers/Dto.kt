package eu.kanade.tachiyomi.extension.cs.mangaboomers

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class EmptyBody

@Serializable
class MangaIdBody(private val mangaId: Int)

@Serializable
class SearchBody(private val genres: List<GenreIdDto>)

@Serializable
class GenreIdDto(private val id: Int)

@Serializable
class SearchDto(val mangas: List<MangaDto>)

@Serializable
class MangaDto(
    private val id: Int,
    val name: String,
    private val thumbnail: String? = null,
    val type: String? = null,
    val translateState: String? = null,
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = thumbnail?.let { baseUrl.resolve(it)?.toString() }
    }
}

@Serializable
class MangaInfoDto(
    private val id: Int,
    private val name: String,
    private val thumbnail: String? = null,
    private val alternateNames: String? = null,
    private val originalName: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val type: String? = null,
    private val state: String? = null,
    private val translateState: String? = null,
    private val genres: List<NameDto> = emptyList(),
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = thumbnail?.let { baseUrl.resolve(it)?.toString() }
        author = this@MangaInfoDto.author?.takeIf { it.isNotBlank() }
        genre = (genres.map { it.name } + listOfNotNull(type?.takeIf { it.isNotBlank() }))
            .joinToString()
            .takeIf { it.isNotEmpty() }
        status = when (translateState?.trim()?.lowercase()) {
            "překládá se" -> SManga.ONGOING
            "dokončeno" -> SManga.COMPLETED
            "hiatus", "pozastaveno" -> SManga.ON_HIATUS
            "zrušeno" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@MangaInfoDto.description?.takeIf { it.isNotBlank() }?.let { html ->
                val body = Jsoup.parseBodyFragment(html).body()
                val paragraphs = body.select("p").ifEmpty { listOf(body) }
                append(paragraphs.map { it.text() }.filter { it.isNotBlank() }.joinToString("\n\n"))
            }
            val altNames = listOfNotNull(originalName, alternateNames).filter { it.isNotBlank() }
            if (altNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternativní názvy: ", altNames.joinToString())
            }
            state?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Stav originálu: ", it)
            }
        }.takeIf { it.isNotEmpty() }
    }
}

@Serializable
class NameDto(val name: String)

@Serializable
class ChapterGroupDto(val chapters: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val id: Int,
    private val nameFixed: String,
    private val created: DateDto? = null,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = id.toString()
        val number = nameFixed.toFloatOrNull()
        name = if (number != null) "$nameFixed. kapitola" else nameFixed
        chapter_number = number ?: -1f
        date_upload = dateFormat.tryParseDateTime(created?.date, siteZone)
        memo = buildJsonObject { put(MANGA_ID, mangaId) }
    }
}

@Serializable
class DateDto(val date: String)

@Serializable
class PagesDto(val pages: List<PageDto>)

@Serializable
class PageDto(val image: String)

@Serializable
class OptionsDto(
    val tags: List<TagDto>,
    val types: List<OptionDto>,
    val states: List<OptionDto>,
)

@Serializable
class TagDto(val id: Int, val name: String)

@Serializable
class OptionDto(val type: String)

const val MANGA_ID = "mangaId"

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
private val siteZone = ZoneId.of("Europe/Prague")
