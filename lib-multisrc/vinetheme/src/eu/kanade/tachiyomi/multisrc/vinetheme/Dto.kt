package eu.kanade.tachiyomi.multisrc.vinetheme

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.util.Locale
import kotlin.time.Instant

@Serializable
class BrowseDto(
    val initialSeries: List<MangaDto>,
    val initialHasMore: Boolean = false,
)

@Serializable
class ApiSeriesResponse(
    val data: List<MangaDto> = emptyList(),
    val meta: ApiPagination? = null,
)

@Serializable
class ApiPagination(
    val hasMore: Boolean = false,
)

@Serializable
class GenreListDto(
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class GenreTagListDto(
    val genres: List<GenreDto> = emptyList(),
    val tags: List<GenreDto> = emptyList(),
) {
    fun toFilterData(): FilterData = FilterData(
        genres.filter { it.count != 0 }.associate { it.displayName to it.genreSlug },
        tags.filter { it.count != 0 }.associate { it.displayName to it.genreSlug },
        FilterData.CURRENT_VERSION,
    )

    companion object {
        fun nextJsFilter(element: JsonElement): Boolean = element is JsonObject && "tagTotal" in element
    }
}

@Serializable
class FilterData(
    val genres: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
    val version: Int = 0,
) {
    val outdated: Boolean
        get() = version < CURRENT_VERSION

    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
class DetailDto(
    val series: MangaDto,
    val chapters: List<ChapterDto> = emptyList(),
    val totalPages: Int = 1,
) {
    companion object {
        fun nextJsFilter(element: JsonElement): Boolean = element is JsonObject && "series" in element && "chapters" in element
    }
}

@Serializable
class ChapterDetailDto(
    val chapter: ChapterPagesDto,
) {
    companion object {
        fun nextJsFilter(element: JsonElement): Boolean = element is JsonObject && element["chapter"]?.let(ChapterPagesDto::nextJsFilter) == true
    }
}

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto> = emptyList(),
) {
    companion object {
        fun nextJsFilter(element: JsonElement): Boolean = element is JsonObject && "pages" in element
    }
}

@Serializable
class PageDto(
    val imageUrl: String? = null,
    val kind: String = "CONTENT",
    val isEncrypted: Boolean = false,
    val isRedacted: Boolean = false,
    val hasFragments: Boolean = false,
    val hasStrips: Boolean = false,
)

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    @SerialName("coverImage")
    val coverUrl: String? = null,
    val slug: String = "",
    val urlSlug: String? = null,
    val status: String = "",
    val type: String = "",
    val origin: String = "",
    val rating: Double = 0.0,
    val isHot: Boolean = false,
    val isMature: Boolean = false,
    val salePercent: Int? = null,
    val originalTitle: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val tags: List<GenreDto> = emptyList(),
    val team: TeamDto? = null,
    val similarSeries: List<MangaDto> = emptyList(),
) {
    fun toSManga(baseUrl: String, manga: SManga = SManga.create(), fallbackSlug: String = ""): SManga = manga.apply {
        val canonicalSlug = listOf(urlSlug.orEmpty(), memo["slug"]?.string.orEmpty(), fallbackSlug)
            .firstOrNull { it.isNotBlank() }
            ?: slug

        title = this@MangaDto.title
        thumbnail_url = coverUrl?.let { it.toAbsoluteUrl(baseUrl) }
        url = id
        memo = buildJsonObject {
            put("id", id)
            put("slug", canonicalSlug)
        }
        status = this@MangaDto.status.toSMangaStatus()
        author = team?.name
        genre = buildList {
            type.takeIf { it.isNotBlank() }?.let(::add)
            origin.takeIf { it.isNotBlank() }?.let(::add)
            if (isMature) add("Mature")
            addAll(genres.map { it.displayName })
            addAll(tags.map { it.displayName })
        }.distinct().joinToString()
        description = buildString {
            this@MangaDto.description?.also {
                append(it.htmlToText(baseUrl))
            }
            val info = buildList {
                rating.takeIf { it > 0 }?.let { add("Rating: ${"%.1f".format(Locale.US, it)}") }
                type.takeIf { it.isNotBlank() }?.let { add("Type: $it") }
                origin.takeIf { it.isNotBlank() }?.let { add("Origin: $it") }
                if (isHot) add("Featured")
                if (isMature) add("Mature")
                salePercent?.takeIf { it > 0 }?.let { add("Sale: $it%") }
            }
            if (info.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(info.joinToString("\n"))
            }
            val altTitles = (listOfNotNull(originalTitle) + aliases)
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.equals(title, ignoreCase = true) }
                .distinct()
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles: \n")
                append(altTitles.joinToString("\n") { "- $it" })
            }
        }.ifEmpty { null }
    }
}

@Serializable
class GenreDto(
    val name: String = "",
    val slug: String = "",
    val genre: GenreSlugDto? = null,
    val count: Int? = null,
) {
    val displayName: String
        get() = (name.ifBlank { genre?.slug.orEmpty() }).stripEmoji().let { if (count == null) it else "$it ($count)" }

    val genreSlug: String
        get() = slug.ifBlank { genre?.slug.orEmpty() }
}

@Serializable
class GenreSlugDto(
    val slug: String = "",
)

@Serializable
class TeamDto(
    val name: String? = null,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: Double,
    val title: String? = null,
    val publishedAt: String? = null,
    @SerialName("isLocked")
    val isLocked: Boolean = false,
    val coinPrice: Int = if (isLocked) 1 else 0,
) {
    fun toSChapter(manga: SManga): SChapter = SChapter.create().apply {
        val numberString = number.toString().removeSuffix(".0")
        name = if (title.isNullOrBlank() || title == numberString) "Chapter $numberString" else title
        if (isLocked) {
            name = "\uD83D\uDD12 $name"
        } else if (coinPrice != 0) {
            name = "\uD83D\uDD13 $name"
        }
        date_upload = Instant.tryParse(publishedAt)
        chapter_number = number.toFloat()
        val mangaSlug = manga.memo["slug"]?.string.orEmpty()
        url = id
        memo = buildJsonObject {
            put("id", id)
            put("slug", mangaSlug)
            put("number", numberString)
            put("isLocked", isLocked)
        }
    }
}

fun String.toSMangaStatus(): Int = when (this) {
    "ONGOING" -> SManga.ONGOING
    "COMPLETED" -> SManga.COMPLETED
    "HIATUS" -> SManga.ON_HIATUS
    "DROPPED" -> SManga.CANCELLED
    "CANCELLED" -> SManga.CANCELLED
    "DISCONTINUED" -> SManga.CANCELLED
    "UPCOMING" -> SManga.ONGOING
    else -> SManga.UNKNOWN
}

fun String.stripEmoji(): String = replace(Regex("[^\\p{ASCII}\\p{L}0-9\\- ]+")) { "" }.trim()

private fun String.htmlToText(baseUrl: String): String {
    val document = Jsoup.parseBodyFragment(this, baseUrl)
    document.select("a[href]").forEach { link ->
        val url = link.absUrl("href")
        val text = link.text()
        link.replaceWith(TextNode(if (text.isBlank()) url else "[$text]($url)"))
    }
    document.select("p").forEach { it.after("\n\n") }
    document.select("br").forEach { it.replaceWith(TextNode("\n")) }
    return document.wholeText().trim()
}

fun String.toAbsoluteUrl(baseUrl: String): String = if (startsWith("http")) this else "$baseUrl$this"
