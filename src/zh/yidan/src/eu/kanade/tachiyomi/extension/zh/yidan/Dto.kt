package eu.kanade.tachiyomi.extension.zh.yidan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Entities

@Serializable
class MangaDto(
    private val title: String,
    private val mhcate: String?,
    private val cateids: String?,
    private val author: String?,
    private val summary: String?,
    private val coverPic: String?,
    private val id: Int,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        author = this@MangaDto.author
        description = summary?.trim()
        genre = when {
            cateids.isNullOrEmpty() -> null
            else -> cateids.split(",").joinToString { GENRES[it.toInt()] }
        }
        status = when {
            mhcate.isNullOrEmpty() -> SManga.ONGOING
            "5" in mhcate.split(",") -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
        thumbnail_url = if (coverPic?.startsWith("http") == true) coverPic else baseUrl + coverPic
        initialized = true
    }
}

@Serializable
class ChapterDto(
    private val createTime: Long,
    private val mhid: String,
    private val title: String,
    private val jiNo: Int,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "$mhid/$jiNo"
        name = Entities.unescape(title)
        date_upload = createTime * 1000L
    }
}

@Serializable
class PageListDto(private val pics: String) {
    val images get() = pics.split(",")
}

@Serializable
class ListingDto(val list: List<MangaDto>, private val total: String) {
    val totalCount get() = total.toInt()
}

@Serializable
class ResponseDto<T>(val data: T)
