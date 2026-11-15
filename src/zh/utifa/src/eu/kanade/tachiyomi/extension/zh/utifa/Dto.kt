package eu.kanade.tachiyomi.extension.zh.utifa

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ApiResponse<T>(
    val message: String,
    val data: T? = null,
)

@Serializable
class MangaListBody(
    private val pageNum: Int,
    private val pageSize: Int,
    private val keyword: String?,
    private val orderBy: String,
    private val order: String,
    private val theme: String?,
    private val grade: String?,
    private val updateStatus: String?,
)

@Serializable
class MangaPageDto(
    val records: List<MangaDto>,
    val current: Int,
    val pages: Int,
)

@Serializable
class MangaDto(
    private val id: Long,
    private val title: String,
    private val cover: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val genres: List<String>? = null,
    private val updateStatus: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(apiUrl: String) = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        thumbnail_url = cover?.let { apiUrl + it }
        description = this@MangaDto.description
        author = this@MangaDto.author
        genre = genres?.joinToString()
        status = when (updateStatus) {
            "0" -> SManga.ONGOING
            "1" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: Long,
    private val title: String,
    private val updateTime: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = title
        date_upload = Instant.tryParse(updateTime)
    }
}

@Serializable
class ChapterPagesDto(
    val chapters: List<FileDto>,
)

@Serializable
class FileDto(
    val url: String,
)

@Serializable
class CategoryDto(
    val key: String,
    val label: String,
    val value: List<CategoryOptionDto>,
)

@Serializable
class CategoryOptionDto(
    val label: String,
    val value: String,
)
