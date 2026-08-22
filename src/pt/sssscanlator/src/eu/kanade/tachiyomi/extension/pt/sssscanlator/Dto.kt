package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Serializable
class LibraryPaginationDto(
    private val page: Int = 1,
    private val totalPages: Int = 1,
) {
    val hasNextPage get() = page < totalPages
}

@Serializable
class LibraryMangaDto(
    private val title: String,
    private val slug: String,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$slug"
        title = this@LibraryMangaDto.title
        thumbnail_url = cover?.takeIf(String::isNotBlank)
    }
}

/** The site keeps flipping between sending payloads as plain JSON and as an encrypted string. */
internal fun JsonElement.decrypted(): JsonElement {
    val encrypted = (this as? JsonPrimitive)?.contentOrNull ?: return this

    return PayloadCipher.decrypt(encrypted).parseAs()
}

internal fun JsonObject.toMangasPage(): MangasPage {
    val payload = values.firstOrNull { it is JsonArray }
        ?: values.firstOrNull { (it as? JsonPrimitive)?.contentOrNull?.let(PayloadCipher::isEncrypted) == true }
        ?: throw Exception("Não foi possível ler a lista de obras")

    val mangas = payload.decrypted().parseAs<List<LibraryMangaDto>>()
    val pagination = get("pagination")?.parseAs<LibraryPaginationDto>() ?: LibraryPaginationDto()

    return MangasPage(mangas.map(LibraryMangaDto::toSManga), pagination.hasNextPage)
}

@Serializable
class SeriesPayloadDto(
    val slug: String,
    private val chapters: JsonElement,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val coverImage: String? = null,
    val status: String? = null,
) {
    val chapterList: List<SChapter>
        get() = chapters.decrypted()
            .parseAs<List<SeriesChapterDto>>()
            .map { it.toSChapter(slug) }
}

@Serializable
class SeriesHeaderDto(
    val seriesId: String,
    val title: String,
)

class SeriesPage(val manga: SManga, val chapters: List<SChapter>)

internal fun String.parseSeriesPage(): SeriesPage {
    var payload: SeriesPayloadDto? = null
    var title: String? = null
    val genres = mutableListOf<String>()

    extractNextJsRsc<JsonElement> { element ->
        when (element) {
            is JsonObject -> {
                if (payload == null && "chapters" in element && "slug" in element) {
                    payload = element.parseAs()
                }
                if (title == null && "seriesId" in element && "title" in element) {
                    title = element.parseAs<SeriesHeaderDto>().title
                }
            }
            is JsonArray -> element.genreBadgeOrNull()?.let(genres::add)
            else -> {}
        }
        false
    }

    val series = payload ?: throw Exception("Não foi possível ler os dados da obra")

    val manga = SManga.create().apply {
        url = "/obra/${series.slug}"
        this.title = title ?: throw Exception("Título da obra não encontrado")
        thumbnail_url = series.coverImage?.takeIf(String::isNotBlank)
        description = series.description?.takeIf(String::isNotBlank)
        author = series.author?.takeIf(String::isNotBlank)
        artist = series.artist?.takeIf(String::isNotBlank)
        genre = genres.distinct().joinToString()
        status = when (series.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELED", "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    return SeriesPage(manga, series.chapterList)
}

private fun JsonArray.genreBadgeOrNull(): String? {
    if (size != 4 || (this[1] as? JsonPrimitive)?.contentOrNull != "span") return null
    if ((this[2] as? JsonPrimitive)?.contentOrNull.isNullOrEmpty()) return null

    val props = this[3] as? JsonObject ?: return null
    if ((props["data-slot"] as? JsonPrimitive)?.contentOrNull != "badge") return null

    return (props["children"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}

@Serializable
class SeriesChapterDto(
    private val id: String,
    private val number: Double,
    private val title: String? = null,
    private val releaseDate: String? = null,
    private val releaseAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val label = number.formatted()
        url = id
        name = title?.takeIf(String::isNotBlank) ?: "Capítulo $label"
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(releaseAt).takeIf { it != 0L } ?: DATE_FORMAT.tryParseDate(releaseDate)
        memo = buildJsonObject {
            put("slug", mangaSlug)
            put("number", label)
        }
    }

    private fun Double.formatted(): String = toString().removeSuffix(".0")

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}

@Serializable
class ChapterPayloadDto(
    val seriesSlug: String,
    private val chapter: JsonElement,
) {
    val pages: List<Page>
        get() = chapter.decrypted()
            .parseAs<ChapterImagesDto>()
            .toPageList()
}

@Serializable
class ChapterImagesDto(
    @SerialName("imagens_lista") private val images: List<String> = emptyList(),
) {
    fun toPageList() = images.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
}
