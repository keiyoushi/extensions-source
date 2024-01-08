package eu.kanade.tachiyomi.multisrc.flixscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class ApiResponse<T>(
    val data: List<T>,
    val meta: PageInfo,
)

@Serializable
data class PageInfo(
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
data class HomeDto(
    val hot: List<BrowseSeries>,
    val topWeek: List<BrowseSeries>,
    val topMonth: List<BrowseSeries>,
    val topAll: List<BrowseSeries>,
)

@Serializable
data class BrowseSeries(
    val id: Int,
    val title: String,
    val slug: String,
    val prefix: Int,
    val thumbnail: String?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = this@BrowseSeries.title
        url = "/series/$prefix-$id-$slug"
        thumbnail_url = thumbnail?.let { cdnUrl + it }
    }
}

@Serializable
data class SearchInput(
    val title: String,
)

@Serializable
data class GenreHolder(
    val name: String,
    val id: Int,
)

@Serializable
data class SeriesResponse(
    val serie: Series,
)

@Serializable
data class Series(
    val id: Int,
    val title: String,
    val slug: String,
    val prefix: Int,
    val thumbnail: String?,
    val story: String?,
    val serieType: String?,
    val mainGenres: String?,
    val otherNames: List<String>? = emptyList(),
    val status: String?,
    val type: String?,
    val authors: List<GenreHolder>? = emptyList(),
    val artists: List<GenreHolder>? = emptyList(),
    val genres: List<GenreHolder>? = emptyList(),
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = this@Series.title
        url = "/series/$prefix-$id-$slug"
        thumbnail_url = cdnUrl + thumbnail
        author = authors?.joinToString { it.name.trim() }
        artist = artists?.joinToString { it.name.trim() }
        genre = (otherGenres + genres?.map { it.name.trim() }.orEmpty())
            .distinct().joinToString { it.trim() }
        description = story?.let { Jsoup.parse(it).text() }
        if (otherNames?.isNotEmpty() == true) {
            if (description.isNullOrEmpty()) {
                description = "Alternative Names:\n"
            } else {
                description += "\n\nAlternative Names:\n"
            }
            description += otherNames.joinToString("\n") { "• ${it.trim()}" }
        }
        status = when (this@Series.status?.trim()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "onhold" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private val otherGenres = listOfNotNull(serieType, mainGenres, type)
        .map { word ->
            word.trim().replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(Locale.getDefault())
                } else {
                    it.toString()
                }
            }
        }
}

@Serializable
data class Chapter(
    val id: Int,
    val name: String,
    val slug: String,
    val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/read/webtoon/$id-$slug"
        name = this@Chapter.name
        date_upload = runCatching { dateFormat.parse(createdAt!!)!!.time }.getOrDefault(0L)
    }

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)
    }
}

@Serializable
data class PageListResponse(
    val chapter: ChapterPages,
)

@Serializable
data class ChapterPages(
    val chapterData: ChapterPageData,
)

@Serializable
data class ChapterPageData(
    val webtoon: List<String>,
)
