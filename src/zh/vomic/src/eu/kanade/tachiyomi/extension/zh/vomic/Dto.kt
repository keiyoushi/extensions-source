package eu.kanade.tachiyomi.extension.zh.vomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat

val SManga.id get() = url.substring(1, 1 + 32)

@Serializable
class MangaDto(
    private val mid: String,
    private val title: String,
    private val site: SiteDto? = null,
    private val cover_img_url: String?,
    private val authors_name: List<String>? = null,
    private val status: String? = null,
    private val categories: JsonElement? = null,
    private val description: String? = null,
) {
    fun toSMangaOrNull() = if (title.isEmpty()) null else toSManga()

    private fun toSManga() = SManga.create().apply {
        url = "/${mid}_c/"
        title = this@MangaDto.title
        thumbnail_url = cover_img_url
    }

    fun toSMangaDetails() = toSManga().apply {
        author = authors_name!!.joinToString()
        description = "站点：" + site + "\n\n" + this@MangaDto.description
        genre = categories!!.jsonArray.joinToString { it.jsonPrimitive.content }
        status = when (this@MangaDto.status!!) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class SiteDto(
    private val site_en: String,
    private val site_cn: String? = null,
) {
    override fun toString() = "$site_cn ($site_en)"
}

val SChapter.id: Pair<String, String>
    get() {
        val url = url
        val length = url.length
        val mangaId = url.substring(length - 32, length)
        val chapterId = url.substring(3, 3 + 32)
        return Pair(mangaId, chapterId)
    }

@Serializable
class ChapterDto(
    private val title: String,
    private val cid: String,
    private val update_time: String,
) {
    fun toSChapter(mangaId: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/m_$cid/chapterimage.ashx?mid=$mangaId"
        name = title
        date_upload = dateFormat.parse(update_time)!!.time
    }
}

@Serializable
class MangaListDto(
    private val page: Int,
    private val result_count: Int,
    private val result: List<MangaDto>,
) {
    val entries get() = if (result_count != 0) result else emptyList()
    val hasNextPage get() = page < 100 && page * 12 < result_count
}

@Serializable
class RankingDto(val result: List<MangaDto>)

@Serializable
class ResponseDto<T>(val data: T)
