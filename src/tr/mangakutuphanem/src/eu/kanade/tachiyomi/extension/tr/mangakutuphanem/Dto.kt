package eu.kanade.tachiyomi.extension.tr.mangakutuphanem

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Serializable
class RecentDto(
    val items: List<SeriesItemDto>,
    val done: Boolean,
)

@Serializable
class ResultsDto(
    val results: List<SeriesItemDto>,
)

@Serializable
class SeriesItemDto(
    private val source: String,
    private val slug: String,
    private val title: String,
    @JsonNames("cover_url") private val coverUrl: String? = null,
    val kind: String? = null,
) {
    fun toSManga(apiUrl: String) = SManga.create().apply {
        url = "/seri-bilgi/$source/$slug"
        title = this@SeriesItemDto.title
        thumbnail_url = coverUrl?.let { coverProxyUrl(apiUrl, it) }
    }
}

@Serializable
class SeriesResponseDto(
    val series: SeriesDto,
    val alternates: List<SeriesItemDto>? = null,
)

@Serializable
class SeriesDto(
    private val sourceName: String,
    private val linkOnly: Boolean,
    private val title: String,
    private val description: String? = null,
    private val coverUrl: String? = null,
    private val status: String? = null,
    private val format: String? = null,
    private val genres: List<String>? = null,
    private val credit: CreditDto? = null,
    private val chapters: List<ChapterDto>,
) {
    fun toSChapters(): List<SChapter> {
        val scanlator = credit?.team?.name ?: sourceName
        return chapters.map { it.toSChapter(scanlator) }
    }

    fun applyTo(manga: SManga, apiUrl: String) = manga.apply {
        title = this@SeriesDto.title
        thumbnail_url = coverUrl?.let { coverProxyUrl(apiUrl, it) }
        author = credit?.author?.name
        description = MarkdownDescription.Builder()
            .info("Bölümler kaynak sitede okunur".takeIf { linkOnly })
            .description(this@SeriesDto.description)
            .field("Kaynak", sourceName)
            .build()
        genre = (listOfNotNull(format?.replaceFirstChar { it.titlecase(Locale.ROOT) }) + genres.orEmpty()).distinct().joinToString()
        status = when (this@SeriesDto.status?.lowercase()) {
            "devam ediyor" -> SManga.ONGOING
            "tamamlandı" -> SManga.COMPLETED
            "ara verildi" -> SManga.ON_HIATUS
            "bırakıldı" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class CreditDto(
    val author: NameDto? = null,
    val team: NameDto? = null,
)

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val title: String,
    private val number: Float? = null,
    private val url: String,
    private val publishedAt: String? = null,
) {
    fun toSChapter(scanlator: String) = SChapter.create().apply {
        url = this@ChapterDto.url
        name = title
        number?.let { chapter_number = it }
        date_upload = parseDate(publishedAt)
        this.scanlator = scanlator
    }
}

@Serializable
class ChapterPagesDto(
    val source: String,
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val ref: String,
    val exp: Long,
    val sig: String,
)

@Serializable
class ErrorDto(
    val error: String,
)

@Serializable
class TokenDto(
    @JsonNames("id_token") val idToken: String,
    @JsonNames("refresh_token") val refreshToken: String,
    @JsonNames("expires_in") val expiresIn: String,
)

@Serializable
class SignUpBody(
    @EncodeDefault private val returnSecureToken: Boolean = true,
)

@Serializable
class SourceDto(
    val id: String,
    val name: String,
    val kind: String,
)

private val TURKISH = Locale.forLanguageTag("tr")
private val ZONE = ZoneId.of("Europe/Istanbul")
private val SLASH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
private val LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", TURKISH)

private val WP_THUMBNAIL_SIZE = Regex("""-\d+x\d+(?=\.\w+$)""")

// Origin cover hosts challenge non-browser clients; the site's proxy serves them. Full size over WordPress thumbnails.
private fun coverProxyUrl(apiUrl: String, coverUrl: String) = "$apiUrl/cover".toHttpUrl().newBuilder()
    .addQueryParameter("u", if ("/wp-content/uploads/" in coverUrl) coverUrl.replace(WP_THUMBNAIL_SIZE, "") else coverUrl)
    .build()
    .toString()

private fun parseDate(date: String?): Long = when {
    date == null -> 0L
    'T' in date -> Instant.tryParse(date)
    '/' in date -> SLASH_DATE.tryParseDate(date, ZONE)
    else -> LONG_DATE.tryParseDate(date, ZONE)
}
