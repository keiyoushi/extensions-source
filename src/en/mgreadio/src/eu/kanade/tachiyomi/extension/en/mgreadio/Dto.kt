package eu.kanade.tachiyomi.extension.en.mgreadio

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class ChapterListDto(
    val items: List<ChapterDto> = emptyList(),
    @SerialName("total_pages")
    val totalPages: Int = 1,
)

@Serializable
class ChapterDto(
    private val title: String = "",
    private val number: Float = -1f,
    private val slug: String = "",
    @SerialName("created_at")
    private val createdAt: String = "",
) {
    fun toSChapter(mangaPath: String): SChapter = SChapter.create().apply {
        val chapterName = if (number % 1f == 0f) number.toInt().toString() else number.toString()
        val cleanMangaPath = mangaPath.substringBefore("/chapter/").trimEnd('/')

        url = "$cleanMangaPath/$slug/"
        name = title.takeIf(String::isNotEmpty)
            ?.let { "Chapter $chapterName - $it" }
            ?: "Chapter $chapterName"
        chapter_number = number
        date_upload = REST_CHAPTER_DATE_FORMAT.tryParse(createdAt)
    }

    companion object {
        private val REST_CHAPTER_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT+7")
        }
    }
}

@Serializable
class MgreadSearchDto(
    val title: String,
    val url: String,
    val thumb: String? = null,
)
