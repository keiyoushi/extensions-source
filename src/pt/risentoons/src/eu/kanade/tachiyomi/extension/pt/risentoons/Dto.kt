package eu.kanade.tachiyomi.extension.pt.risentoons

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class MangaListDto(
    private val data: List<MangaDto>,
    private val page: Int,
    private val limit: Int,
    private val total: Int,
) {
    fun toMangasPage(baseUrl: String) = MangasPage(
        data.map { it.toSManga(baseUrl) },
        page * limit < total,
    )
}

@Serializable
class DataDto<T>(val data: T)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val synopsis: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
    @SerialName("alternative_names") private val alternativeNames: List<AlternativeNameDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = coverImage?.let { baseUrl + it }
        author = this@MangaDto.author?.takeIf { it.isNotBlank() }
        artist = this@MangaDto.artist?.takeIf { it.isNotBlank() }
        genre = genres.joinToString()
        description = buildString {
            synopsis?.let { append(it) }
            if (alternativeNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Nomes alternativos:\n")
                alternativeNames.joinTo(this, "\n") { "• ${it.name}" }
            }
        }.ifEmpty { null }
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class AlternativeNameDto(val name: String)

@Serializable
class ChapterListDto(val chapters: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Double,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val numberText = number.toString().removeSuffix(".0")
        url = "/biblioteca/$mangaSlug/$numberText/read?chapter=$id"
        name = title?.takeIf { it.isNotBlank() } ?: "Capítulo $numberText"
        chapter_number = number.toFloat()
        date_upload = dateFormat.tryParseDateTime(createdAt, ZoneOffset.UTC)
    }
}

@Serializable
class PageListDto(private val pages: List<PageDto>) {
    fun toPageList(baseUrl: String) = pages.sortedBy { it.pageNumber }
        .mapIndexed { i, page -> Page(i, imageUrl = baseUrl + page.imageUrl) }
}

@Serializable
class PageDto(
    @SerialName("page_number") val pageNumber: Int,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
class GenreDto(val name: String)

private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME
