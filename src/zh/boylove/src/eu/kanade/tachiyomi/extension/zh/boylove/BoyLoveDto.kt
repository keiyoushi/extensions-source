package eu.kanade.tachiyomi.extension.zh.boylove

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC)

@Serializable
class MangaDto(
    val id: Int,
    private val title: String,
    @SerialName("update_time") private val updateTime: JsonPrimitive? = null,
    private val image: String,
    @SerialName("auther") private val authorName: String,
    private val desc: String? = null,
    @SerialName("mhstatus") private val mhStatus: Int,
    private val keyword: String,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        author = authorName
        genre = keyword.replace(",", ", ")
        status = when (mhStatus) {
            0 -> SManga.ONGOING
            1 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = image.toImageUrl()
        val rawUpdateTime = updateTime
        if (rawUpdateTime == null) {
            description = desc?.trim()
            return@apply
        }
        val timeStr = when {
            rawUpdateTime.isString -> rawUpdateTime.content
            else -> dateFormat.format(Instant.ofEpochSecond(rawUpdateTime.long))
        }
        description = "更新时间：$timeStr\n\n${desc?.trim()}"
        initialized = true
    }
}

fun String.toImageUrl() = if (startsWith("http")) {
    this
} else {
    "https://blcnimghost2.cc$this"
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val title: String,
    @SerialName("create_time") private val createTime: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/home/book/capter/id/$id"
        name = title.trim()
        date_upload = dateFormat.tryParseDateTime(createTime)
    }
}

@Serializable
class ListPageDto<T>(val lastPage: Boolean, val list: List<T> = emptyList())

@Serializable
class ResultDto<T>(val result: T)
