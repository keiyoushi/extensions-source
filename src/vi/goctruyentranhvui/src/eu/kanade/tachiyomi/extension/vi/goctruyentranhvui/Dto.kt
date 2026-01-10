package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class ResultDto<T>(
    val result: T,
)

@Serializable
class ChapterDto(
    private val comicId: String,
    private val numberChapter: String,
    private val updateTime: Long,
) {
    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        name = numberChapter
        date_upload = updateTime
        url = "/truyen/$slug/chuong-$numberChapter#$comicId"
    }
}

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ListingDto(
    val next: Boolean,
    val data: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val id: String,
    private val name: String,
    private val description: String,
    private val statusCode: String,
    private val photo: String,
    private val nameEn: String,
    private val author: String,
    private val category: List<String>? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = name
        thumbnail_url = baseUrl + photo
        url = "$id:$nameEn"
        author = this@MangaDto.author
        description = this@MangaDto.description
        genre = category?.joinToString()
        status = when (statusCode) {
            "PRG" -> SManga.ONGOING
            "END" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class ImageListDto(
    val data: List<String>? = null,
)
