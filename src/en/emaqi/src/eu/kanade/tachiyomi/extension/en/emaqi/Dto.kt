package eu.kanade.tachiyomi.extension.en.emaqi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

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
    private val input: SearchInput,
)

@Suppress("unused")
@Serializable
class SearchInput(
    private val keyword: String,
    private val tagSlugGroups: List<TagGroup>,
    private val page: Int,
    private val limit: Int,
)

@Suppress("unused")
@Serializable
class TagGroup(
    private val tagSlugs: List<String>,
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
    val homeSection: HomeSection,
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
        url = comicId
        title = this@Comic.title
        thumbnail_url = cover?.url
        memo = buildJsonObject {
            put("slug", slug)
        }
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
class ComicDataResponse(
    val comicVolumes: ComicVolumes,
    val chapters: List<Chapter>,
)

@Serializable
class ComicVolumes(
    val comic: ComicDetails,
    val volumes: List<Volume>,
)

@Serializable
class ComicDetails(
    val slug: String,
    private val title: String,
    private val synopsis: String?,
    private val rating: Int?,
    private val creators: List<String>?,
    private val publisher: String?,
    private val completed: Boolean?,
    private val cover: Cover?,
    private val genres: List<Genre>?,
) {
    fun toSManga() = SManga.create().apply {
        title = this@ComicDetails.title
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
        status = if (completed == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = cover?.url
        memo = buildJsonObject {
            put("slug", slug)
        }
    }
}

@Serializable
class Genre(
    val name: String,
)

@Serializable
class Chapter(
    private val comicId: String,
    private val chapterNumber: Int,
    private val name: String,
    private val purchased: Boolean?,
    private val free: Boolean?,
    private val releasesAt: String?,
) {
    val isLocked: Boolean
        get() = purchased == false && free == false

    fun toSChapter(comicSlug: String) = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = chapterNumber.toString()
        name = lock + this@Chapter.name
        date_upload = Instant.tryParse(releasesAt)
        chapter_number = chapterNumber.toFloat()
        memo = buildJsonObject {
            put("type", "chapter")
            put("comicId", comicId)
            put("slug", comicSlug)
        }
    }
}

@Serializable
class Volume(
    private val comicId: String,
    private val volumeNumber: Int,
    private val eisbn: String?,
    private val slug: String,
    private val name: String,
    private val trialPage: Int?,
    private val purchased: Boolean?,
    private val free: Boolean?,
    private val releasesAt: String?,
) {
    val isLocked: Boolean
        get() = purchased == false && free == false

    private val isPreview: Boolean
        get() = isLocked && trialPage != null && trialPage > 0

    fun toSChapter(comicSlug: String) = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val preview = if (isPreview) "(Preview) " else ""
        url = eisbn ?: volumeNumber.toString()
        name = lock + preview + this@Volume.name.ifEmpty { "Oneshot" }
        date_upload = Instant.tryParse(releasesAt)
        chapter_number = volumeNumber.toFloat()
        memo = buildJsonObject {
            put("type", "volume")
            put("comicId", comicId)
            put("slug", if (slug.isEmpty()) comicSlug else "$comicSlug-$slug")
            put("volumeNumber", volumeNumber)
        }
    }
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
    val pages: List<ContentPage>,
    val hash: String,
)

@Serializable
class ContentPage(
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
