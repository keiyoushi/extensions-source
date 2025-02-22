package eu.kanade.tachiyomi.extension.en.infinityscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class ResponseDto<T>(val result: T)

@Serializable
data class SearchResultDto(
    val titles: List<SearchEntryDto>,
    val genres: List<FilterDto>? = null,
    val authors: List<FilterDto>? = null,
    val statuses: List<String>? = null,
)

@Serializable
data class RankingResultDto(
    val weekly: List<SearchEntryDto>,
    val monthly: List<SearchEntryDto>,
    val all: List<SearchEntryDto>,
)

@Serializable
data class SearchEntryDto(
    val id: Int,
    val title: String,
    val slug: String,
    val cover: String,
    val authors: String?,
    val genres: String?,
    val all_views: Int? = null,
    val status: String?,
    val updated: Int? = null,
) {
    fun toSManga(cdnHost: String) = SManga.create().apply {
        title = this@SearchEntryDto.title
        thumbnail_url = "https://$cdnHost/$id/$cover?_=${getImageParameter()}"
        url = "/comic/$id/$slug"
    }

    private fun getImageParameter(): Long {
        return updated?.toLong() ?: 0L
    }
}

@Serializable
data class FilterDto(val id: Int, val title: String)

@Serializable
data class ChapterEntryDto(
    val id: Int,
    val title: String,
    val sequence: Int,
    val date: Int,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        name = title

        // Things like prologues mess up the sequence number
        chapter_number = title.substringAfter("hapter ").toFloatOrNull() ?: sequence.toFloat()
        date_upload = date.toLong() * 1000
        url = "$slug/chapter/$id"
    }
}

@Serializable
data class PageEntryDto(
    val link: String,
)
