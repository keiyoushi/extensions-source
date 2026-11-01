package eu.kanade.tachiyomi.extension.zh.xipimanhua

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ApiResponse<T>(
    val message: String? = null,
    val data: T? = null,
)

@Serializable
class MangaListDto(
    val items: List<MangaListItemDto>,
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaListItemDto(
    private val mid: String,
    private val title: String,
    @SerialName("vertical_image_url") private val cover: String? = null,
) {
    fun toSManga() = mangaOf(mid, title, cover)
}

@Serializable
class SearchDto(
    val data: List<SearchItemDto>,
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class SearchItemDto(
    private val id: String,
    private val title: String,
    @SerialName("vertical_image_url") private val cover: String? = null,
) {
    fun toSManga() = mangaOf(id, title, cover)
}

// Listings give the works path ("<mid>-<slug>"); only the mid identifies the series
private fun mangaOf(worksPath: String, title: String, cover: String?) = SManga.create().apply {
    url = worksPath.substringBefore('-')
    this.title = title.trim()
    thumbnail_url = cover?.let { COVER_URL + it }
}

@Serializable
class MangaDto(
    private val title: String,
    private val description: String? = null,
    private val authors: List<NameDto> = emptyList(),
    private val categories: List<NameDto> = emptyList(),
    private val genres: List<NameDto> = emptyList(),
    private val status: String? = null,
    @SerialName("vertical_image_url") private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title.trim()
        description = this@MangaDto.description
        author = authors.joinToString { it.name.trim() }.ifEmpty { null }
        genre = (categories + genres).joinToString { it.name.trim() }.ifEmpty { null }
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        thumbnail_url = cover?.let { COVER_URL + it }
    }
}

@Serializable
class NameDto(val name: String)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class ChapterDto(
    private val hid: String,
    private val title: String,
    @SerialName("updated_at") private val updatedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = hid
        name = title
        date_upload = Instant.tryParse(updatedAt)
    }
}

@Serializable
class ChapterPagesDto(
    val images: String,
    @SerialName("order_id") val orderId: Long,
    val sid: Long,
    val line: Int,
)

private const val COVER_URL = "https://cover.s3imgs.top"
