package eu.kanade.tachiyomi.extension.en.infinityscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class ResponseDto<T>(val result: T)

@Serializable
data class SearchResultDto(
    val pages: Int,
    val comics: List<SearchEntryDto>,
    val genres: List<FilterDto>? = null,
    val authors: List<FilterDto>? = null,
    val statuses: List<String>? = null,
)

@Serializable
data class SearchEntryDto(
    val id: Int,
    val title: String,
    val slug: String,
    val cover: String,
    val updated: String? = null,
    val created: String? = null,
) {
    fun toSManga(cdnHost: String) = SManga.create().apply {
        title = this@SearchEntryDto.title
        thumbnail_url = "https://$cdnHost/$id/$cover?v=${getImageParameter()}"
        url = "/comic/$id/$slug"
    }

    private fun getImageParameter(): Long {
        val date = updated?.let { parseDate(it, DATE_FORMATTER) }
            ?: created?.let { parseDate(it, DATE_FORMATTER) }
            ?: 0L
        return date / 1000L
    }
}

@Serializable
data class FilterDto(val id: Int, val title: String)

@Serializable
data class ChapterDataDto(val chapters: List<ChapterEntryDto>)

@Serializable
data class ChapterEntryDto(
    val id: Int,
    val title: String,
    val sequence: Int,
    val date: String,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        name = title

        // Things like prologues mess up the sequence number
        chapter_number = title.substringAfter("hapter ").toFloatOrNull() ?: sequence.toFloat()
        date_upload = parseDate(date, CHAPTER_FORMATTER)
        url = "$slug/chapter/$id"
    }
}

@Serializable
data class PageDataDto(val images: List<PageEntryDto>)

@Serializable
data class PageEntryDto(val link: String)

private val DATE_FORMATTER by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
}

private val CHAPTER_FORMATTER by lazy {
    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
}

private fun parseDate(dateStr: String, formatter: SimpleDateFormat): Long {
    return runCatching { formatter.parse(dateStr)?.time }
        .getOrNull() ?: 0L
}
