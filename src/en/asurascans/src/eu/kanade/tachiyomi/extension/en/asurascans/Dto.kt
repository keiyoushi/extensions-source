package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class DataDto<T>(
    val data: T? = null,
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class MangaDto(
    @SerialName("public_url")
    val publicUrl: String,
    val slug: String,
    private val title: String,
    @JsonNames("coverUrl")
    private val cover: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = cover
        url = "/series/$slug" // Keep the old URL structure for compatibility with existing bookmarks
        memo = buildJsonObject {
            put("slug", "$baseUrl$publicUrl".toHttpUrl().pathSegments.last())
        }
    }
}

@Serializable
class MangaDetailsDto(
    private val title: String,
    private val coverUrl: String,
    private val author: String? = null,
    private val artist: String? = null,
    private val description: String? = null,
    private val rating: Double? = null,
    private val popularityRank: Int? = null,
    @SerialName("alternativeTitles")
    private val altTitles: String? = null,
    private val genres: List<GenreDto>? = null,
    private val status: String? = null,
) {
    fun toSMangaDetails() = SManga.create().apply {
        title = this@MangaDetailsDto.title
        thumbnail_url = coverUrl
        author = this@MangaDetailsDto.author
        artist = this@MangaDetailsDto.artist
        description = parseDescription()
        genre = genres?.joinToString { it.name }
        status = parseStatus()
        initialized = true
    }

    fun parseDescription(): String = buildString {
        val plainDescription = description?.let { Jsoup.parseBodyFragment(it).text() }
        plainDescription?.let(::append)

        popularityRank?.let {
            if (isNotEmpty()) append("\n\n")
            append("Rank: #$it")
        }

        rating?.let {
            if (isNotEmpty()) append("\n\n")
            append("Rating: %.2f".format(it))
        }

        val cleanAltTitles = altTitles
            ?.split(" • ")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }

        if (!cleanAltTitles.isNullOrEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("Alternative Titles:\n")
            cleanAltTitles.joinTo(this, "\n") { "- $it" }
        }
    }

    fun parseStatus() = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped", "axed" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class MangaUrlDto(
    val publicUrl: String,
    val seriesSlug: String,
) {
    fun apply(manga: SManga, baseUrl: String) = manga.apply {
        url = "/series/$seriesSlug" // Keep the old URL structure for compatibility with existing bookmarks
        memo = buildJsonObject {
            put("slug", "$baseUrl$publicUrl".toHttpUrl().pathSegments.last())
        }
    }
}

@Serializable
class AvailableGenres(
    val availableGenres: List<GenreDto>,
)

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class Creators(
    val artists: List<String>,
    val authors: List<String>,
)

@Serializable
class FiltersDto(
    val genres: List<GenreDto>,
    val artists: List<String>,
    val authors: List<String>,
)

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>? = null,
)

@Serializable
class ChapterDto(
    private val number: Float,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String = "",
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("series_slug") private val seriesSlug: String? = null,
) {
    fun toSChapter(randomMangaSlug: String) = SChapter.create().apply {
        val numberStr = number.toString().removeSuffix(".0")
        url = "/series/$seriesSlug/chapter/$numberStr"
        memo = buildJsonObject {
            put("mangaSlug", randomMangaSlug)
        }
        name = buildString {
            if (isLocked) append("🔒 ")
            append("Chapter $numberStr")
            title?.let { append(" - $it") }
        }

        date_upload = runCatching {
            Instant.parse(createdAt).toEpochMilliseconds()
        }.getOrDefault(0L)
    }
}

@Serializable
class PremiumPageListDto(
    val data: ChapterWrapper,
) {
    @Serializable
    class ChapterWrapper(
        val chapter: PageListDto,
    )
}

@Serializable
class PageListDto(
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val url: String,
    val tiles: List<Int>? = null,
    @SerialName("tile_cols")
    val tileCols: Int? = null,
    @SerialName("tile_rows")
    val tileRows: Int? = null,
)

@Serializable
class PageData(
    val tiles: List<Int>,
    val tileCols: Int,
    val tileRows: Int,
)
