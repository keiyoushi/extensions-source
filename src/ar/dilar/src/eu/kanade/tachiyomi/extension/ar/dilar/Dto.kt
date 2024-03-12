package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float

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
    @SerialName("time_stamp") private val timestamp: Long,

    @SerialName("has_rev_link") private val hasRevLink: Boolean,
    @SerialName("support_link") private val supportLink: String,
) {
    val isMonetized get() = hasRevLink && supportLink.isNotEmpty()

    fun toSChapter() = SChapter.create().apply {
        url = "/r/$id"
        chapter_number = chapter.float
        date_upload = timestamp * 1000
        scanlator = teamName

        val chapterName = title.let { if (it.trim() != "") " - $it" else "" }
        name = "${chapter_number.let { if (it % 1 > 0) it else it.toInt() }}$chapterName"
    }
}
