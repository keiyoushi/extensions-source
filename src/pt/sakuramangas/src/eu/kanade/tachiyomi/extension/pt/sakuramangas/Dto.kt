package eu.kanade.tachiyomi.extension.pt.sakuramangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Serializable
class LatestDto(
    @SerialName("titulo") private val title: String,
    @SerialName("url_manga") private val url: String,
    private val thumb: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = this@LatestDto.url
        title = this@LatestDto.title
        thumbnail_url = thumb?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
    }
}

@Serializable
class CardDto(
    private val url: String,
    private val title: String,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = this@CardDto.url
        title = this@CardDto.title
        thumbnail_url = cover
    }
}

@Serializable
class DetailsDto(
    private val title: String,
    private val author: String,
    private val description: String,
    private val status: String,
    private val demographic: String,
    private val cover: String? = null,
    private val genres: List<String>,
) {
    fun toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = this@DetailsDto.title.ifEmpty { throw Exception("A página da obra não carregou") }
        author = this@DetailsDto.author.ifEmpty { null }
        description = MarkdownDescription.Builder().description(this@DetailsDto.description).build()
        genre = (genres + demographic).filter { it.isNotEmpty() }.distinct().joinToString().ifEmpty { null }
        status = when (this@DetailsDto.status.lowercase()) {
            "em andamento", "ativo" -> SManga.ONGOING
            "finalizado", "completo", "concluído" -> SManga.COMPLETED
            "hiato", "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        if (status == SManga.COMPLETED) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        thumbnail_url = cover
    }
}

@Serializable
class ChapterDto(
    private val number: String,
    private val url: String,
    private val date: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = this@ChapterDto.url
        name = "Capítulo $number"
        chapter_number = number.toFloat()
        date_upload = parseDate(date)
    }
}

@Serializable
class MangaResult(
    val details: DetailsDto? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val complete: Boolean = false,
)

@Serializable
class BrowseResult(
    val cards: List<CardDto> = emptyList(),
)

@Serializable
class Gate(
    val challenge: Boolean,
    val captcha: Boolean,
    val rate: Boolean,
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val RELATIVE_REGEX = Regex("""h[áa]\s+(\d+)\s+(minuto|hora|dia|semana|m[êe]s|ano)""")

// The chapter list shows relative dates ("Há 1 ano", "Há 3 dias"); the site's own absolute dates are a fallback
private fun parseDate(text: String): Long {
    val value = text.trim().lowercase()
    val match = RELATIVE_REGEX.find(value) ?: return DATE_FORMAT.tryParseDate(value)
    val amount = match.groupValues[1].toInt()
    val unit: Duration = when (match.groupValues[2]) {
        "minuto" -> 1.minutes
        "hora" -> 1.hours
        "dia" -> 1.days
        "semana" -> 7.days
        "mes", "mês" -> 30.days
        else -> 365.days
    }
    return (Clock.System.now() - unit * amount).toEpochMilliseconds()
}
