package eu.kanade.tachiyomi.extension.en.emaqi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Variables
@Suppress("unused")
@Serializable
class SeriesVariables(
    private val slug: String,
    private val mangaAfter: String?,
)

@Suppress("unused")
@Serializable
class SearchVariables(
    private val input: Input,
)

@Serializable
class Input(
    @Suppress("unused")
    private val keyword: String,
)

@Suppress("unused")
@Serializable
class DetailsVariables(
    private val comicId: String,
)

@Suppress("unused")
@Serializable
class ChapterViewerVariables(
    private val comicId: String,
    private val chapterNumber: Int,
)

@Suppress("unused")
@Serializable
class VolumeViewerVariables(
    private val comicId: String,
    private val volumeNumber: Int,
)

// Responses
@Serializable
class SeriesResponse(
    @JsonNames("genre") val homeSection: HomeSection,
)

@Serializable
class HomeSection(
    val mangaConn: MangaConn,
)

@Serializable
class MangaConn(
    val edges: List<Edge>,
    val pageInfo: PageInfo,
)

@Serializable
class Edge(
    val node: Node,
)

@Serializable
class PageInfo(
    val hasNextPage: Boolean,
    val endCursor: String,
)

@Serializable
class Node(
    val comic: Comic,
)

@Serializable
class Comic(
    private val comicId: String,
    private val slug: String,
    private val title: String,
    private val cover: Cover?,
) {
    fun toSManga() = SManga.create().apply {
        url = "$comicId#$slug"
        title = this@Comic.title
        thumbnail_url = cover?.url
    }
}

@Serializable
class Cover(
    val url: String?,
)

@Serializable
class SearchResponse(
    val search: List<Comic>,
)

@Serializable
class DetailsResponse(
    val manga: Manga,
)

@Serializable
class Manga(
    val comic: DeatilsComic,
)

@Serializable
class DeatilsComic(
    private val title: String,
    private val synopsis: String?,
    private val rating: Int?,
    private val creators: List<String>?,
    private val publisher: String?,
    private val metadata: Metadata?,
    private val cover: Cover?,
    private val genres: List<Genre>?,
) {
    fun toSManga() = SManga.create().apply {
        title = this@DeatilsComic.title
        author = creators?.joinToString()
        description = buildString {
            synopsis?.let { append(it) }
            publisher?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nPublisher: $it")
            }

            if (rating != null) {
                append("\n\nAge limit: $rating+")
            }
        }
        genre = genres?.joinToString { it.name }
        status = if (metadata?.completed == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = cover?.url
    }
}

@Serializable
class Metadata(
    val completed: Boolean?,
)

@Serializable
class Genre(
    val name: String,
)

@Serializable
class ChapterVolumeResponse(
    val comicVolumes: ComicVolumes,
    val chapters: List<Chapter>,
)

@Serializable
class ComicVolumes(
    val volumes: List<Volume>,
)

@Serializable
class Chapter(
    private val comicId: String,
    private val chapterNumber: Int?,
    private val name: String,
    private val purchased: Boolean?,
    private val free: Boolean?,
    private val releasesAt: String?,
) {
    val isLocked: Boolean
        get() = purchased == false && free == false

    fun toSChapter(slug: String) = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = "$comicId/chapter/$chapterNumber/$slug"
        name = lock + this@Chapter.name
        date_upload = dateFormat.tryParse(releasesAt)
        chapter_number = chapterNumber?.toFloat() ?: -1f
    }
}

@Serializable
class Volume(
    private val comicId: String,
    private val trialPage: Int?,
    private val slug: String,
    private val volumeNumber: Int?,
    private val name: String,
    private val purchased: Boolean?,
    private val free: Boolean?,
    private val releasesAt: String?,
) {
    val isLocked: Boolean
        get() = purchased == false && free == false

    val isPreview: Boolean
        get() = isLocked && trialPage != null && trialPage > 0

    fun toSChapter(urlSlug: String) = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val preview = if (isPreview) "(Preview) " else ""
        url = "$comicId/volume/$volumeNumber/$urlSlug/$slug"
        name = lock + preview + this@Volume.name
        date_upload = dateFormat.tryParse(releasesAt)
        chapter_number = volumeNumber?.toFloat() ?: -1f
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class ViewerResponse(
    @JsonNames("manga") val chapter: Viewer,
)

@Serializable
class Viewer(
    val contents: Contents?,
)

@Serializable
class Contents(
    val pages: List<Page>,
    val hash: String,
)

@Serializable
class Page(
    val url: String,
)

@Suppress("unused")
@Serializable
class LoginRequestBody(
    private val email: String,
    private val password: String,
    private val returnSecureToken: Boolean,
)

@Suppress("unused")
@Serializable
class RefreshRequestBody(
    @SerialName("grant_type") private val grantType: String,
    @SerialName("refresh_token") private val refreshToken: String,
)

@Serializable
class LoginResponse(
    @JsonNames("id_token") val idToken: String,
    @JsonNames("refresh_token") val refreshToken: String,
)
