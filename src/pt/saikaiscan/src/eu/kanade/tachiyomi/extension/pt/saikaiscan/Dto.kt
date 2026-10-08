package eu.kanade.tachiyomi.extension.pt.saikaiscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class Result<T>(
    val data: T? = null,
    private val meta: Meta? = null,
) {

    val hasNextPage: Boolean
        get() = meta !== null && meta.currentPage < meta.lastPage
}

typealias PaginatedStories = Result<List<Story>>
typealias ReleaseResult = Result<Release>
typealias GenresResult = Result<List<GenreDto>>

@Serializable
class Meta(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class Story(
    private val artists: List<Person> = emptyList(),
    private val authors: List<Person> = emptyList(),
    private val genres: List<GenreDto> = emptyList(),
    private val image: String,
    val releases: List<Release> = emptyList(),
    val slug: String,
    private val status: StatusDto? = null,
    private val synopsis: String,
    private val title: String,
) {

    fun toSManga(storageUrl: String): SManga = SManga.create().apply {
        title = this@Story.title
        author = authors.joinToString { it.name }
        artist = artists.joinToString { it.name }
        genre = genres.joinToString { it.name }
        status = when (this@Story.status?.name) {
            "Concluído" -> SManga.COMPLETED
            "Em Andamento" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = Jsoup.parseBodyFragment(synopsis)
            .select("p")
            .joinToString("\n\n") { it.text() }
        thumbnail_url = "$storageUrl/$image"
        url = "/comics/$slug"
    }
}

@Serializable
class Person(
    val name: String,
)

@Serializable
class GenreDto(
    val name: String,
    val id: Int,
)

@Serializable
class StatusDto(
    val name: String,
)

@Serializable
class Release(
    private val chapter: String,
    private val id: Int,
    @SerialName("is_active") val isActive: Int = 1,
    @SerialName("published_at") private val publishedAt: String,
    @SerialName("release_images") val releaseImages: List<ReleaseImage> = emptyList(),
    private val slug: String,
    private val title: String? = "",
) {

    fun toSChapter(storySlug: String): SChapter = SChapter.create().apply {
        name = "Capítulo $chapter" +
            (if (this@Release.title.isNullOrEmpty().not()) " - ${this@Release.title}" else "")
        chapter_number = chapter.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedAt)
        scanlator = "Saikai Scan"
        url = "/ler/comics/$storySlug/$id/$slug"
    }
}

@Serializable
class ReleaseImage(val image: String)
