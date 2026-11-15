package eu.kanade.tachiyomi.extension.zh.tibiucomics

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jsoup.nodes.Entities
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class ComicInfoDto(val data: ComicNameDto? = null)

@Serializable
class ComicNameDto(val name: String)

@Serializable
class ChapterDto(
    private val name: String,
    private val link: String,
    private val addtime: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = link
        name = Entities.unescape(this@ChapterDto.name)
        date_upload = DATE_FORMAT.tryParseDate(addtime, ZoneId.of("Asia/Shanghai"))
    }
}

@Serializable
class PicListDto(
    val code: Int,
    val msg: String,
    private val data: JsonElement? = null,
) {
    fun toPages() = data!!.parseAs<List<PicDto>>()
        .sortedBy { it.xid }
        .mapIndexed { index, pic -> Page(index, imageUrl = pic.img) }
}

@Serializable
class PicDto(
    val xid: Int,
    val img: String,
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
