package eu.kanade.tachiyomi.extension.en.infinityscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ResponseDto<T>(val result: T)

@Serializable
class SearchResultDto(
    val titles: List<SearchEntryDto>,
    val genres: List<FilterDto>? = null,
    val authors: List<FilterDto>? = null,
    val statuses: List<String>? = null,
)

@Serializable
class RankingResultDto(
    val weekly: List<SearchEntryDto>,
    val monthly: List<SearchEntryDto>,
    val all: List<SearchEntryDto>,
)

@Serializable
class SearchEntryDto(
    private val id: Int,
    val title: String,
    private val slug: String,
    private val cover: String,
    val authors: String?,
    val genres: String?,
    @SerialName("all_views") val allViews: Int? = null,
    val status: String?,
    val updated: Int? = null,
) {
    fun toSManga(cdnHost: String) = SManga.create().apply {
        title = this@SearchEntryDto.title
        thumbnail_url = "https://$cdnHost/$id/$cover?_=${getImageParameter()}"
        url = "/comic/$id/$slug"
    }

    private fun getImageParameter(): Long = updated?.toLong() ?: 0L
}

@Serializable
class FilterDto(val id: Int, val title: String)

@Serializable
class ChapterEntryDto(
    private val id: Int,
    private val title: String,
    private val sequence: Int,
    private val date: Int,
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
class PageEntryDto(
    val link: String,
)
