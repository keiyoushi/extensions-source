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
    private val numberChapter: String,
    private val updateTime: Long,
) {
    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        name = numberChapter
        date_upload = updateTime
        url = "/truyen/$slug/chuong-$numberChapter"
    }
}

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ListingDto(
    val p: Int? = null,
    val next: Boolean? = null,
    val data: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val id: String,
    private val name: String,
    private val photo: String,
    private val nameEn: String,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = name
        thumbnail_url = baseUrl + photo
        url = "$id:$nameEn"
    }
}

@Serializable
class ImageListWrapper(
    val headers: Map<String, String> = emptyMap(),
    val body: ResultDto<ImageListDto>,
)

@Serializable
class ImageListDto(
    val data: List<String>,
)
