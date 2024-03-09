package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import java.text.SimpleDateFormat

@Serializable
class ChapterListDto(
    val releases: List<ChapterRelease>,
)

@Serializable
class ChapterRelease(
    private val id: Int,
    private val chapter: JsonPrimitive,
    private val title: String,
    @SerialName("team_name") private val teamName: String,
    @SerialName("created_at") private val createdAt: String,

    private val has_rev_link: Boolean,
    private val support_link: String,
) {
    val isMonetized get() = has_rev_link && support_link.isNotEmpty()

    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/r/$id"
        chapter_number = chapter.float
        date_upload = try {
            dateFormat.parse(createdAt)!!.time
        } catch (_: Exception) {
            0L
        }
        scanlator = teamName

        val chapterName = title.let { if (it.trim() != "") " - $it" else "" }
        name = "${chapter_number.let { if (it % 1 > 0) it else it.toInt() }}$chapterName"
    }
}
