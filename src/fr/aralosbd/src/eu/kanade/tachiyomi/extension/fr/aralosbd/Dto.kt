package eu.kanade.tachiyomi.extension.fr.aralosbd

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val zone = ZoneId.of("Europe/Paris")

@Serializable
class AralosBDSearchResult(
    @SerialName("page_count") val pageCount: Int,
    val mangas: List<AralosBDSearchManga>,
)

@Serializable
class AralosBDSearchManga(
    private val icon: String,
    private val title: String,
    private val id: Int,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@AralosBDSearchManga.title
        thumbnail_url = "$baseUrl/$icon"
        url = "$baseUrl/manga/display?id=$id"
    }
}

@Serializable
class AralosBDManga(
    @SerialName("main_title") val mainTitle: String,
    val fulldescription: String? = null,
    val description: String,
    val authors: List<AralosBDAuthor>? = null,
    val tags: List<AralosBDTag>? = null,
    val icon: String,
)

@Serializable
class AralosBDAuthor(val name: String)

@Serializable
class AralosBDTag(val tag: String)

@Serializable
class AralosBDChapter(
    @SerialName("chapter_number") private val chapterNumber: String,
    @SerialName("chapter_title") private val chapterTitle: String,
    @SerialName("chapter_translator") private val chapterTranslator: String? = null,
    @SerialName("chapter_id") private val chapterId: Int,
    @SerialName("chapter_released") private val chapterReleased: Int,
    @SerialName("chapter_release_time") private val chapterReleaseTime: String? = null,
) {
    val isReleased get() = chapterReleased == 1

    fun toSChapter(baseUrl: String) = SChapter.create().apply {
        url = "$baseUrl/manga/chapter?id=$chapterId"
        name = "$chapterNumber - $chapterTitle"
        date_upload = dateFormat.tryParseDateTime(chapterReleaseTime, zone)
        // chapter_number is a string and it can be 2.5.1 for example
        scanlator = chapterTranslator
    }
}

@Serializable
class AralosBDPages(val links: List<String>)
