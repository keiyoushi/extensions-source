package eu.kanade.tachiyomi.extension.en.arknightscomics

import android.net.Uri
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.stringOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup

@Serializable
class ApiResponse<T>(val data: Rows<T>)

@Serializable
class Rows<T>(val rows: List<T>)

@Serializable
class Comic(
    val name: String,
    // a plain string for most comics, a one-item array for newer ones
    private val smallImage: JsonElement,
    private val comicDescribe: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/comic?name=${Uri.encode(name)}"
        title = name
        thumbnail_url = when (smallImage) {
            is JsonArray -> smallImage.firstOrNull()?.stringOrNull
            else -> smallImage.stringOrNull
        }
        description = comicDescribe?.let { Jsoup.parseBodyFragment(it).wholeText().trim() }
    }
}

@Serializable
class ComicChapter(
    @SerialName("name") private val comicName: String,
    private val chapter: Int,
    private val detailsName: String,
    private val publishTime: Long,
    val detailsContent: List<String>,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/comic-detail?name=${Uri.encode(comicName)}&chapter=$chapter"
        name = detailsName
        chapter_number = chapter.toFloat()
        date_upload = publishTime
    }
}
