package eu.kanade.tachiyomi.extension.ar.mangatales

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class ChapterListDto(
    val mangaReleases: List<ChapterRelease>,
)

@Serializable
class ChapterRelease(
    private val id: Int,
    private val chapter: JsonPrimitive,
    private val title: String,
    @SerialName("team_name") private val teamName: String,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter() = SChapter.create().apply {
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

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

@Serializable
class ReaderDto(
    val readerDataAction: ReaderData,
    val globals: Globals,
)

@Serializable
class Globals(
    val mediaKey: String,
)

@Serializable
class ReaderData(
    val readerData: ReaderChapter,
)

@Serializable
class ReaderChapter(
    val release: ReaderPages,
)

@Serializable
class ReaderPages(
    @SerialName("hq_pages") private val page: String,
) {
    val pages get() = page.split("\r\n")
}
