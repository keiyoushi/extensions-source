package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

@Serializable
class CatalogDto(val state: StateDto)

@Serializable
class StateDto(
    private val books: List<BookDto>,
    private val chapters: List<UploadDto>,
) {
    val publishedBooks get() = books.filter { it.visibility == "Publicada" }

    fun chaptersOf(book: BookDto): List<SChapter> {
        val imported = book.importedChapters.filter { it.status == "Publicado" }.map { it.toSChapter(book.id) }
        val uploaded = chapters.filter { it.book == book.id && it.status == "Publicado" }.map { it.toSChapter(book.id) }
        return (imported + uploaded).sortedByDescending { it.chapter_number }
    }
}

@Serializable
class BookDto(
    val id: String,
    val title: String,
    private val author: String,
    val genres: List<String>,
    val status: String,
    val visibility: String,
    private val adult: Boolean,
    private val rating: Double,
    private val ratingCount: Int = 0,
    val views: Int,
    val periodViews: PeriodViewsDto? = null,
    private val cover: String,
    private val synopsis: String,
    val sourceUrl: String? = null,
    val importedChapters: List<ImportedChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = id
        title = this@BookDto.title
        thumbnail_url = cover.takeIf(String::isNotBlank)?.let { baseUrl.resolve(it)?.toString() }
        author = this@BookDto.author.ifBlank { null }
        genre = (genres + listOfNotNull("+18".takeIf { adult })).joinToString().ifEmpty { null }
        status = when (this@BookDto.status) {
            "En emisión" -> SManga.ONGOING
            "Finalizada" -> SManga.COMPLETED
            "En pausa" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = listOfNotNull(
            "★ ${rating.toString().removeSuffix(".0")}/10 · $ratingCount valoraciones".takeIf { ratingCount > 0 },
            synopsis.replace("\r", "").replace(BLANK_LINES, "\n\n").trim().ifEmpty { null },
        ).joinToString("\n\n").ifEmpty { null }
        initialized = true
    }
}

@Serializable
class PeriodViewsDto(
    val day: Int,
    val week: Int,
)

@Serializable
class ImportedChapterDto(
    private val number: Double,
    private val label: String,
    private val title: String,
    private val date: String,
    val status: String,
) {
    fun toSChapter(bookId: String) = SChapter.create().apply {
        url = chapterPath(bookId, number)
        val label = if (label.firstOrNull()?.isDigit() == true) "Capítulo ${label.replace(LEADING_ZEROS, "")}" else label
        name = if (title.isBlank() || label.contains(title, ignoreCase = true)) label else "$label - $title"
        chapter_number = number.toFloat()
        date_upload = dateFormat.tryParseDateTime(date, siteZone)
    }
}

@Serializable
class UploadDto(
    val book: String,
    private val number: Double,
    private val title: String,
    val status: String,
    private val date: String? = null,
) {
    fun toSChapter(bookId: String) = SChapter.create().apply {
        url = chapterPath(bookId, number)
        name = "Capítulo ${number.toString().removeSuffix(".0")}" + if (title.isBlank()) "" else " - $title"
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(date)
    }
}

private fun chapterPath(bookId: String, number: Double) = "/leer/$bookId/${number.toString().removeSuffix(".0")}"

@Serializable
class ReaderDto(val chapter: ReaderChapterDto)

@Serializable
class ReaderChapterDto(val pages: List<PageDto>)

@Serializable
class PageDto(val src: String)

private val BLANK_LINES = Regex("""\n{3,}""")

private val LEADING_ZEROS = Regex("""^0+(?=\d)""")

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private val siteZone = ZoneId.of("America/Santiago")
