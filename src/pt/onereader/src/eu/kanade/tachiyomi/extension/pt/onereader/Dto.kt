package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Serializable
internal class CatalogDto(
    val works: List<WorkDto>,
    val pagination: PaginationDto,
)

@Serializable
internal class UpdatesDto(
    val items: List<WorkDto>,
    val pagination: PaginationDto,
)

@Serializable
internal class PaginationDto(
    private val page: Int,
    private val pages: Int? = null,
    private val totalPages: Int? = null,
) {
    val hasNextPage get() = page < (pages ?: totalPages!!)
}

@Serializable
internal class WorkDetailsDto(
    val work: WorkDto,
    val chapters: List<ChapterDto>,
)

@Serializable
internal class WorkDto(
    private val id: String,
    private val title: String,
    private val originalName: String? = null,
    private val englishTitle: String? = null,
    private val nativeTitle: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val synopsis: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val contentType: String? = null,
    private val originCountry: String? = null,
    private val releaseYear: Int? = null,
    private val genres: List<String>? = null,
    private val coverUrl: String? = null,
    private val publisher: PublisherDto? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = this@WorkDto.title
        thumbnail_url = coverUrl
        author = this@WorkDto.author?.takeIf(String::isNotBlank)
        artist = this@WorkDto.artist?.takeIf(String::isNotBlank)
        genre = (listOfNotNull(format) + genres.orEmpty()).distinct().joinToString().ifEmpty { null }
        status = this@WorkDto.status.toStatus()
        description = buildDescription()
    }

    private val format get() = (contentType ?: type)?.takeIf(String::isNotBlank)

    private fun buildDescription(): String? {
        val facts = listOfNotNull(
            originCountry?.takeIf(String::isNotBlank),
            releaseYear?.toString(),
            publisher?.name?.takeIf(String::isNotBlank),
        ).joinToString(" · ")
        val altTitles = listOfNotNull(originalName, englishTitle, nativeTitle)
            .filter { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
            .distinct()
            .joinToString("\n") { "- $it" }
            .takeIf(String::isNotEmpty)
            ?.let { "**Títulos alternativos:**\n$it" }

        val link = publisher?.workUrl?.takeIf(String::isNotBlank)?.let { "**Links:**\n- [${publisher.name?.takeIf(String::isNotBlank) ?: it}]($it)" }

        return listOf(facts, synopsis.orEmpty(), altTitles.orEmpty(), link.orEmpty())
            .filter(String::isNotBlank)
            .joinToString("\n\n")
            .ifEmpty { null }
    }
}

@Serializable
internal class PublisherDto(
    val name: String? = null,
    val workUrl: String? = null,
)

@Serializable
internal class ChapterDto(
    private val number: Double,
    private val title: String? = null,
    private val postedAt: String? = null,
    val accessLocked: Boolean,
) {
    private val displayNumber get() = number.toString().removeSuffix(".0")

    fun toSChapter(mangaKey: String): SChapter = SChapter.create().apply {
        val chapterName = "Capítulo $displayNumber"
        url = "$mangaKey/$displayNumber"
        memo = buildJsonObject {
            put("id", mangaKey)
            put("number", displayNumber)
            put("locked", accessLocked)
        }
        name = buildString {
            if (accessLocked) append("🔒 ")
            append(chapterName)
            title?.takeIf { it.isNotBlank() && it != chapterName }?.let { append(": ", it) }
        }
        chapter_number = number.toFloat()
        date_upload = postedAt.toChapterDate()
    }
}

@Serializable
internal class FilterData(
    val genres: Map<String, List<GenreDto>>,
)

@Serializable
internal class GenreDto(
    val name: String,
)

@Serializable
internal class ManifestDto(
    val protection: ProtectionDto,
    val chapter: ManifestChapterDto,
)

@Serializable
internal class ProtectionDto(
    val transport: TransportDto? = null,
)

@Serializable
internal class TransportDto(
    val serverKey: String,
)

@Serializable
internal class ManifestChapterDto(
    val pages: List<String>,
)

@Serializable
internal class MediaDto(
    val mode: String,
    val url: String,
    val key: String? = null,
    val keyWrap: KeyWrapDto? = null,
    val contentType: String? = null,
)

@Serializable
internal class KeyWrapDto(
    val serverKey: String,
    val context: String,
    val iv: String,
    val payload: String,
)

@Serializable
internal class ErrorDto(
    val code: String? = null,
)

private fun String?.toStatus(): Int = when (this?.trim()?.lowercase(Locale.ROOT)) {
    "em lançamento", "lançando" -> SManga.ONGOING
    "completo" -> SManga.COMPLETED
    "hiatus", "hiato" -> SManga.ON_HIATUS
    "cancelado" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private fun String?.toChapterDate(): Long = when {
    this == null -> 0L
    endsWith("Z") -> Instant.tryParse(this)
    else -> chapterDateFormat.tryParseDateTime(this, ZoneOffset.UTC)
}

private val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
