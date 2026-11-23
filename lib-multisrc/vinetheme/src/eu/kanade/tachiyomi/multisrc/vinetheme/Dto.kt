package eu.kanade.tachiyomi.multisrc.vinetheme

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class ApiSeriesResponse(
    val data: List<MangaDto> = emptyList(),
    val meta: ApiPagination?,
)

@Serializable
class ApiPagination(
    val hasMore: Boolean = false,
)

@Serializable
class GenreTagListDto(
    private val genres: List<GenreDto> = emptyList(),
    private val tags: List<GenreDto> = emptyList(),
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
    private val version: Int = 0,
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
class RelatedDto(
    val similarSeries: List<MangaDto> = emptyList(),
) {
    companion object {
        fun nextJsFilter(element: JsonElement): Boolean = element is JsonObject && "similarSeries" in element
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
    val id: String,
    val imageUrl: String?,
    val fragmentMap: String?,
    val fragmentMapIv: String?,
    val fragments: List<FragmentDto> = emptyList(),
)

@Serializable
class FragmentDto(
    val fragmentIndex: Int,
    val imageUrl: String,
)

@Serializable
class FragmentKeyDto(
    val key: String,
)

@Serializable
class FragmentMapDto(
    val w: Int,
    val h: Int,
    val fragments: List<FragmentRectDto>,
)

@Serializable
class FragmentRectDto(
    val idx: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

@Serializable
class PageLayout(
    val width: Int,
    val height: Int,
    val parts: List<PagePart>,
)

@Serializable
class PagePart(
    val url: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    @SerialName("coverImage")
    private val coverUrl: String?,
    private val slug: String = "",
    private val urlSlug: String?,
    private val status: String = "",
    private val type: String = "",
    private val origin: String = "",
    private val rating: Double = 0.0,
    private val ratingCount: Int?,
    private val isHot: Boolean = false,
    private val isMature: Boolean = false,
    private val salePercent: Int?,
    private val originalTitle: String?,
    private val aliases: List<String> = emptyList(),
    private val description: String?,
    private val genres: List<GenreDto> = emptyList(),
    private val tags: List<GenreDto> = emptyList(),
    val team: TeamDto?,
) {
    val isComic: Boolean
        get() = type in COMIC_TYPES

    fun toSManga(baseUrl: String, manga: SManga = SManga.create(), fallbackSlug: String = "", author: String? = null): SManga = manga.apply {
        val canonicalSlug = listOf(urlSlug.orEmpty(), memo["slug"]?.string.orEmpty(), fallbackSlug)
            .firstOrNull { it.isNotBlank() }
            ?: slug

        title = this@MangaDto.title
        thumbnail_url = coverUrl?.toAbsoluteUrl(baseUrl)
        url = id
        memo = buildJsonObject {
            put("id", id)
            put("slug", canonicalSlug)
        }
        status = this@MangaDto.status.toSMangaStatus()
        this.author = author
        genre = buildList {
            type.takeIf { it.isNotBlank() }?.let { add(it.toLabel()) }
            origin.takeIf { it.isNotBlank() }?.let { add(it.toLabel()) }
            if (isMature) add("Mature")
            addAll(genres.map { it.displayName })
            addAll(tags.map { it.displayName })
        }.distinct().joinToString()
        description = MarkdownDescription.Builder()
            // Scored out of 10, shown by the site as five stars
            .rating(rating.takeIf { it > 0 }?.div(2), max = 5, votes = ratingCount)
            .info(
                "Featured".takeIf { isHot },
                "Mature".takeIf { isMature },
                salePercent?.takeIf { it > 0 }?.let { "Sale: $it%" },
            )
            .field("Type", type.toLabel())
            .field("Origin", origin.toLabel())
            .separator()
            .description(this@MangaDto.description?.let { Jsoup.parseBodyFragment(it, baseUrl).body() })
            .altTitles((listOfNotNull(originalTitle) + aliases).filterNot { it.trim().equals(title, ignoreCase = true) })
            .build()
    }
}

@Serializable
class GenreDto(
    private val name: String = "",
    private val slug: String = "",
    private val genre: GenreSlugDto?,
    val count: Int?,
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
    val name: String?,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Double,
    private val title: String?,
    private val publishedAt: String?,
    val isLocked: Boolean = false,
    private val coinPrice: Int = if (isLocked) 1 else 0,
) {
    fun toSChapter(manga: SManga, team: String?): SChapter = SChapter.create().apply {
        val numberString = number.toString().removeSuffix(".0")
        name = if (title.isNullOrBlank() || title == numberString) "Chapter $numberString" else title
        if (isLocked) {
            name = "\uD83D\uDD12 $name"
        } else if (coinPrice != 0) {
            name = "\uD83D\uDD13 $name"
        }
        date_upload = Instant.tryParse(publishedAt)
        scanlator = team
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

@Serializable
class JsonLdScriptDto(
    @SerialName("__html")
    val html: String,
) {
    companion object {
        fun nextJsFilter(element: JsonElement): Boolean = element is JsonObject && element.getStringOrNull("__html")?.contains(""""@type":"Book"""") == true
    }
}

@Serializable
class JsonLdBookDto(
    val author: JsonLdPersonDto?,
)

@Serializable
class JsonLdPersonDto(
    val name: String?,
)

private val COMIC_TYPES = setOf("MANHWA", "MANHUA", "MANGA", "WEBTOON")

private fun String.toLabel(): String = lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private fun String.toSMangaStatus(): Int = when (this) {
    "ONGOING" -> SManga.ONGOING
    "COMPLETED" -> SManga.COMPLETED
    "HIATUS" -> SManga.ON_HIATUS
    "DROPPED" -> SManga.CANCELLED
    "CANCELLED" -> SManga.CANCELLED
    "DISCONTINUED" -> SManga.CANCELLED
    "UPCOMING" -> SManga.ONGOING
    else -> SManga.UNKNOWN
}

private val EMOJI_REGEX = Regex("""[^\p{ASCII}\p{L}0-9\- ]+""")

private fun String.stripEmoji(): String = replace(EMOJI_REGEX, "").trim()

internal fun String.toAbsoluteUrl(baseUrl: String): String? = baseUrl.toHttpUrl().resolve(this)?.toString()
