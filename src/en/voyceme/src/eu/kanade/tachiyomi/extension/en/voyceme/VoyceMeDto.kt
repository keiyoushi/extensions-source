package eu.kanade.tachiyomi.extension.en.voyceme

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class VoyceMeComic(
    val author: VoyceMeAuthor? = null,
    val chapters: List<VoyceMeChapter> = emptyList(),
    val description: String? = "",
    val genres: List<VoyceMeGenreAggregation> = emptyList(),
    val id: Int = -1,
    val slug: String = "",
    val status: String? = "",
    val thumbnail: String = "",
    val title: String = "",
) {

    fun toSManga(): SManga = SManga.create().apply {
        title = this@VoyceMeComic.title
        author = this@VoyceMeComic.author?.username.orEmpty()
        description = Parser
            .unescapeEntities(this@VoyceMeComic.description.orEmpty(), true)
            .let { Jsoup.parseBodyFragment(it).text() }
        status = when (this@VoyceMeComic.status.orEmpty()) {
            "completed" -> SManga.COMPLETED
            "ongoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        genre = genres.mapNotNull { it.genre?.title }.joinToString(", ")
        url = "/series/$slug"
        thumbnail_url = VoyceMe.STATIC_URL + thumbnail
    }
}

@Serializable
data class VoyceMeAuthor(
    val username: String? = "",
)

@Serializable
data class VoyceMeGenreAggregation(
    val genre: VoyceMeGenre? = null,
)

@Serializable
data class VoyceMeGenre(
    val title: String? = "",
)

@Serializable
data class VoyceMeChapter(
    @SerialName("created_at") val createdAt: String = "",
    val id: Int = -1,
    val images: List<VoyceMePage> = emptyList(),
    val title: String = "",
) {

    fun toSChapter(comicSlug: String): SChapter = SChapter.create().apply {
        name = title
        date_upload = runCatching { DATE_FORMATTER.parse(createdAt)?.time }
            .getOrNull() ?: 0L
        url = "/series/$comicSlug/$id#comic"
    }

    companion object {
        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    }
}

@Serializable
data class VoyceMePage(
    val image: String = "",
)

@Serializable
data class GraphQlQuery<T>(
    val variables: T,
    val query: String,
)

@Serializable
data class GraphQlResponse<T>(val data: T)

typealias VoyceMeSeriesResponse = GraphQlResponse<VoyceMeSeriesCollection>
typealias VoyceMeChapterImagesResponse = GraphQlResponse<VoyceChapterImagesCollection>

@Serializable
data class VoyceMeSeriesCollection(
    @SerialName("voyce_series")
    val series: List<VoyceMeComic> = emptyList(),
)

@Serializable
data class VoyceChapterImagesCollection(
    @SerialName("voyce_chapter_images")
    val images: List<VoyceMePage> = emptyList(),
)

@Serializable
data class PopularQueryVariables(
    val offset: Int,
    val limit: Int,
)

@Serializable
data class SearchQueryVariables(
    val offset: Int,
    val limit: Int,
    val searchTerm: String,
)

@Serializable
data class DetailsQueryVariables(val slug: String)

@Serializable
data class PagesQueryVariables(val chapterId: Int)

typealias LatestQueryVariables = PopularQueryVariables
typealias ChaptersQueryVariables = DetailsQueryVariables
