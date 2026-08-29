package eu.kanade.tachiyomi.extension.ru.wamanga

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import okhttp3.Response
import kotlin.time.Instant

// ─────────────────────────────────────────────────────────────────────────────
// SvelteKit `__data.json`
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
class SvelteResponseDto(
    private val nodes: List<SvelteNodeDto>,
) {
    /** The page's own payload is the last `data` node; earlier ones belong to layouts. */
    fun getDataNode(): JsonArray = nodes.lastOrNull { it.type == "data" }?.data
        ?: throw IllegalStateException("Data node not found in SvelteKit response")
}

@Serializable
class SvelteNodeDto(
    val type: String,
    val data: JsonArray? = null,
)

/**
 * Expands SvelteKit's `devalue` array into a plain JSON tree.
 * In that format every object/array value is an integer index pointing back
 * into the flat pool, and negative indices are placeholders (`-1` = undefined).
 */
internal fun JsonArray.decodeSvelte(): JsonElement {
    if (isEmpty()) throw IllegalStateException("Empty data array in SvelteKit response")
    return resolve(this, this[0])
}

private fun resolve(pool: JsonArray, element: JsonElement): JsonElement = when (element) {
    is JsonArray -> JsonArray(element.map { resolveRef(pool, it) })
    is JsonObject -> buildJsonObject {
        element.forEach { (key, value) -> put(key, resolveRef(pool, value)) }
    }
    else -> element
}

private fun resolveRef(pool: JsonArray, element: JsonElement): JsonElement {
    val index = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        ?: return resolve(pool, element)

    if (index < 0) return JsonNull
    if (index !in pool.indices) return element

    return when (val value = pool[index]) {
        is JsonArray, is JsonObject -> resolve(pool, value)
        else -> value
    }
}

internal inline fun <reified T> Response.parseSvelte(): T = parseAs<SvelteResponseDto>().getDataNode().decodeSvelte().parseAs<T>()

// ─────────────────────────────────────────────────────────────────────────────
// Catalog
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
class CatalogDto(
    val initialMangas: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val type: String,
    private val coverUrl: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "$type/$slug"
        title = this@MangaDto.title
        thumbnail_url = coverUrl.toAbsoluteUrl(baseUrl)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Details + chapters
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
class DetailsDto(
    val manga: MangaDetailsDto,
    val sameMangas: List<MangaDetailsDto> = emptyList(),
)

@Serializable
class MangaDetailsDto(
    private val slug: String,
    private val title: String,
    private val titleEnglish: String? = null,
    private val type: String,
    private val year: Int? = null,
    private val coverUrl: String? = null,
    private val description: String? = null,
    private val alternateTitles: List<String> = emptyList(),
    private val genres: List<String> = emptyList(),
    private val authors: List<String>? = null,
    private val artists: List<String>? = null,
    private val statusTitle: String? = null,
    private val pegiRating: String? = null,
    private val likes: Int? = null,
    private val views: Int? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    val mangaUrl get() = "$type/$slug"

    fun toSManga(baseUrl: String): SManga {
        val fullDescription = buildDescription()

        return SManga.create().apply {
            url = mangaUrl
            title = this@MangaDetailsDto.title
            thumbnail_url = coverUrl.toAbsoluteUrl(baseUrl)
            author = authors.toCreditString()
            artist = artists.toCreditString()
            genre = genres.joinToString()
            status = when (statusTitle?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "paused", "on hiatus", "hiatus" -> SManga.ON_HIATUS
                "discontinued", "cancelled", "abandoned" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            description = fullDescription
        }
    }

    /**
     * The site stores the slug among [alternateTitles], so drop it along with
     * anything already shown as the main title. Entries are filled in by hand,
     * so they may also carry stray whitespace or be left blank.
     */
    private fun cleanAlternateTitles(): List<String> {
        val excluded = setOf(slug, title.trim())

        return (alternateTitles + listOfNotNull(titleEnglish))
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in excluded }
            .distinct()
    }

    private fun buildDescription() = buildString {
        if (description != null && description.isNotBlank()) {
            append(description.trim())
        }

        val stats = listOfNotNull(
            views?.let { "Просмотров: ${formatCount(it)}" },
            likes?.let { "Лайков: ${formatCount(it)}" },
            year?.let { "Год выпуска: $it" },
            pegiRating?.takeIf { it.isNotBlank() }?.let { "Возрастное ограничение: $it" },
        )
        if (stats.isNotEmpty()) {
            append("\n\n")
            append(stats.joinToString("\n"))
        }

        val altTitles = cleanAlternateTitles()
        if (altTitles.isNotEmpty()) {
            append("\n\nАльтернативные названия:")
            altTitles.forEach { append("\n• $it") }
        }
    }.trim()
}

@Serializable
class ChapterDto(
    private val position: JsonPrimitive,
    private val createdAt: String? = null,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/${position.content}"
        name = "Глава ${position.content}"
        chapter_number = position.content.toFloatOrNull() ?: -1f
        date_upload = createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds() ?: 0L
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pages
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
class PagesDto(
    val chapter: ChapterPagesDto,
)

@Serializable
class ChapterPagesDto(
    val files: List<FileDto>,
)

@Serializable
class FileDto(
    private val diskFile: String,
    private val position: JsonPrimitive,
) {
    /** Comes through as a string like `"1.000000000"`. */
    val order get() = position.content.toDoubleOrNull() ?: 0.0

    fun toPage(index: Int, baseUrl: String) = Page(index, imageUrl = "$baseUrl$diskFile")
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Placeholder the site uses for unknown authors/artists. */
private const val NOT_AVAILABLE = "N/A"

/**
 * Paths come back with a leading slash, e.g. `/app/uploads/.../cover.webp`,
 * but strip it before joining anyway in case the API ever stops sending it.
 */
private fun String?.toAbsoluteUrl(baseUrl: String): String? = this?.takeIf { it.isNotBlank() }?.let { "$baseUrl/${it.removePrefix("/")}" }

private fun List<String>?.toCreditString(): String? = this
    ?.filter { it != NOT_AVAILABLE }
    ?.joinToString()
    ?.takeIf { it.isNotBlank() }

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> {
        val m = count / 1_000_000
        val d = (count % 1_000_000) / 100_000
        if (d == 0) "${m}M" else "$m.${d}M"
    }
    count >= 1_000 -> {
        val k = count / 1_000
        val d = (count % 1_000) / 100
        if (d == 0) "${k}K" else "$k.${d}K"
    }
    else -> count.toString()
}
