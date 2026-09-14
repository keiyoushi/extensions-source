package eu.kanade.tachiyomi.extension.tr.mangadenizi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class MangaApiResponse<T>(
    val data: T,
)

@Serializable
class MangaIndexData(
    val manga: MangaListDataDto,
)

@Serializable
class MangaDetailsData(
    val manga: MangaDto,
)

@Serializable
class MangaListDataDto(
    val data: List<MangaDto>,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class MangaDto(
    private val title: String,
    val slug: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    @SerialName("cover_thumb_url") private val coverThumbUrl: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    private val categories: List<CategoryDto> = emptyList(),
    private val genres: List<CategoryDto> = emptyList(),
    private val authors: List<AuthorDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        url = "/manga/$slug"
        thumbnail_url = coverUrl ?: coverThumbUrl
        description = this@MangaDto.description
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = (categories + genres).map { it.name }.distinct().joinToString()
        author = authors.joinToString { it.name }
    }
}

@Serializable
class CategoryDto(
    val name: String,
)

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val number: JsonElement,
    private val title: String? = null,
    private val slug: String,
    @SerialName("published_at") private val publishedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/read/$mangaSlug/$slug"
        val numberStr = number.jsonPrimitive.contentOrNull ?: number.toString()
        name = "Bölüm $numberStr" + (if (title.isNullOrBlank()) "" else ": $title")
        chapter_number = numberStr.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ReaderDto(
    val pages: List<ReaderPageDto> = emptyList(),
)

@Serializable
class ReaderPageDto(
    @SerialName("image_url") private val url: String,
    private val scramble: ScrambleDto? = null,
) {
    fun toPage(index: Int): Page {
        val pageUrl = if (scramble?.method == "tiled-v1" && scramble.grid != null && scramble.seed != null) {
            url.toHttpUrl().newBuilder()
                .addQueryParameter(UnscramblerInterceptor.PARAM_GRID, scramble.grid.toString())
                .addQueryParameter(UnscramblerInterceptor.PARAM_SEED, scramble.seed.toString())
                .build().toString()
        } else {
            url
        }
        return Page(index, imageUrl = pageUrl)
    }
}

@Serializable
class ScrambleDto(
    val method: String? = null,
    val grid: Int? = null,
    val seed: Long? = null,
)
