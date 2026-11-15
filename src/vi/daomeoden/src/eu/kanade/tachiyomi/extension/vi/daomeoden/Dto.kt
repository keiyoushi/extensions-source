package eu.kanade.tachiyomi.extension.vi.daomeoden

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val chapterDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm", Locale.ROOT)
private val chapterDateZone = ZoneId.of("Asia/Ho_Chi_Minh")

@Serializable
class SearchResponse(
    val data: List<BookDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
)

@Serializable
class PaginationDto(
    val page: Int = 1,
    val limit: Int = 0,
    val total: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("has_next") val hasNext: Boolean = false,
)

@Serializable
class ImagesDto(
    val cover: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
)

@Serializable
class NamedDto(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
)

@Serializable
class BookDto(
    val id: Int = 0,
    val name: String = "",
    val title: String = "",
    val slug: String = "",
    val url: String = "",
    val status: String? = null,
    @SerialName("is_adult") val isAdult: Boolean = false,
    val images: ImagesDto = ImagesDto(),
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = name.ifEmpty { this@BookDto.title }
        url = this@BookDto.url
        thumbnail_url = images.coverUrl
        status = parseStatus(this@BookDto.status)
    }
}

@Serializable
class MangaDetailResponse(
    val data: MangaDetailDto = MangaDetailDto(),
)

@Serializable
class MangaDetailDto(
    val id: Int = 0,
    val name: String = "",
    val title: String = "",
    val url: String = "",
    val description: String? = null,
    val status: NamedDto? = null,
    val author: String? = null,
    val images: ImagesDto = ImagesDto(),
    val categories: List<NamedDto> = emptyList(),
    val tags: List<NamedDto> = emptyList(),
    val recommendations: JsonElement = JsonNull,
) {
    fun toSManga(mangaUrl: String): SManga = SManga.create().apply {
        url = mangaUrl
        title = name.ifEmpty { this@MangaDetailDto.title }
        thumbnail_url = images.coverUrl
        status = parseStatus(this@MangaDetailDto.status?.name)
        author = this@MangaDetailDto.author?.takeIf { it.isNotBlank() && it != "Đang cập nhật" }
        genre = (categories + tags).map { it.name }.distinct().joinToString().ifEmpty { null }
        description = this@MangaDetailDto.description
            ?.let { Jsoup.parseBodyFragment(it).text() }
    }
}

@Serializable
class ChapterListResponse(
    val data: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Int = 0,
    @SerialName("manga_id") val mangaId: Int = 0,
    val number: Float = 0f,
    val title: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    val url: String = "",
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = this@ChapterDto.url
        name = title.ifEmpty { "Chapter $number" }
        chapter_number = number
        date_upload = chapterDateFormat.tryParseDateTime(updatedAt, chapterDateZone)
    }
}

@Serializable
class ChapterDetailResponse(
    val data: ChapterDetailDto = ChapterDetailDto(),
)

@Serializable
class ChapterDetailDto(
    val id: Int = 0,
    val url: String = "",
    val images: List<String> = emptyList(),
)

@Serializable
class FiltersResponse(
    val data: FilterDataDto = FilterDataDto(),
)

@Serializable
class FilterDataDto(
    val categories: List<FilterOptionDto> = emptyList(),
    val tags: List<FilterOptionDto> = emptyList(),
)

@Serializable
class FilterOptionDto(
    val id: Int = 0,
    val name: String,
    val slug: String,
)

private fun parseStatus(statusText: String?): Int = when (statusText?.trim()?.lowercase()) {
    "ongoing", "đang ra" -> SManga.ONGOING
    "completed", "hoàn thành", "full" -> SManga.COMPLETED
    "hiatus", "tạm ngưng" -> SManga.ON_HIATUS
    "cancelled", "dropped", "drop" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
