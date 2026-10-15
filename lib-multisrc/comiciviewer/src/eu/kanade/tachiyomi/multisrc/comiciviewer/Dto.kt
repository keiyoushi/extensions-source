package eu.kanade.tachiyomi.multisrc.comiciviewer

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

@Serializable
class RankingResponse(
    val children: List<
        @Serializable(RankingMangaSerializer::class)
        Ranking,
        >,
)

@Serializable
class Ranking(
    private val hash: String,
    private val img: RankingImg,
) {
    fun toSManga() = SManga.create().apply {
        url = hash
        title = img.alt
        thumbnail_url = img.thumbnail
    }
}

@Serializable
class RankingImg(
    val alt: String,
    private val src: String?,
    private val srcSet: String?,
) {
    val thumbnail: String?
        get() = srcSet?.substringAfterLast(", ")?.substringBefore(" ") ?: src
}

object RankingMangaSerializer : JsonTransformingSerializer<Ranking>(Ranking.serializer()) {
    override fun transformDeserialize(element: JsonElement) = buildJsonObject {
        val tuple = element.jsonArray
        put("hash", tuple[2])
        put("img", tuple.findImg()!!)
    }
}

internal fun JsonElement.findImg(): JsonObject? = when (this) {
    is JsonObject -> takeIf { "src" in it } ?: values.firstNotNullOfOrNull { it.findImg() }
    is JsonArray -> firstNotNullOfOrNull { it.findImg() }
    else -> null
}

@Serializable
class SearchApiResponse(
    val searchResult: SearchResult,
)

@Serializable
class SearchResult(
    val series: SeriesResult,
)

@Serializable
class SeriesResult(
    val total: Int,
    val series: List<SeriesSummary>,
)

@Serializable
class ApiResponse(
    val series: SeriesData,
)

@Serializable
class SeriesData(
    val summary: SeriesSummary,
    val episodes: List<Episode> = emptyList(),
)

@Serializable
class SeriesSummary(
    private val id: String,
    private val name: String,
    private val description: String?,
    private val author: List<Author>?,
    private val images: List<SeriesImage>?,
    private val tag: List<Tag>?,
    private val isCompleted: Boolean?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = name
        author = this@SeriesSummary.author?.joinToString { it.name }
        artist = author
        description = this@SeriesSummary.description
            ?.takeIf { it.isNotBlank() }
            ?.parseAs<List<DescriptionNode>>()
            ?.joinToString("\n") { node -> node.children.joinToString("") { it.resolveText().orEmpty() } }
        genre = tag?.joinToString { it.name }
        thumbnail_url = images?.firstOrNull()?.url
        status = if (isCompleted == true) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class Author(
    val name: String,
)

@Serializable
class SeriesImage(
    val url: String,
)

@Serializable
class Tag(
    val name: String,
)

@Serializable
class DescriptionNode(
    val children: List<DescriptionChild>,
)

@Serializable
class DescriptionChild(
    val text: String?,
    val url: String?,
    val children: List<DescriptionChild>?,
) {
    fun resolveText(): String? = when {
        text != null -> text
        children != null -> {
            val inner = children.joinToString("") { it.resolveText().orEmpty() }
            if (url != null) "[$inner]($url)" else inner
        }
        else -> null
    }
}

@Serializable
class Episode(
    val id: String,
    private val title: String,
    private val datePublished: Long?,
) {
    fun toSChapter(access: EpisodeAccess?): SChapter = SChapter.create().apply {
        url = id
        val lock = when {
            access?.needsLogin == true -> "➡️ "
            access?.isLocked == true -> "🔒 "
            else -> ""
        }
        name = lock + title
        datePublished?.let { date_upload = it * 1000L }
        if (access?.needsLogin == true) {
            memo = buildJsonObject {
                put("login", true)
            }
        }
    }
}

@Serializable
class AccessApiResponse(
    val seriesAccess: SeriesAccess,
)

@Serializable
class SeriesAccess(
    val episodeAccesses: List<EpisodeAccess>,
)

@Serializable
class EpisodeAccess(
    val episodeId: String,
    private val hasAccess: Boolean,
    private val accessType: String,
) {
    val isLocked: Boolean
        get() = !hasAccess

    val needsLogin: Boolean
        get() = isLocked && accessType == "memberOnlyFree"
}

@Serializable
class EpisodeDetailsApiResponse(
    val episode: EpisodeDetails,
)

@Serializable
class EpisodeDetails(
    val content: List<EpisodeContent>,
    val contentId: Int,
) {
    val viewerId: String?
        get() = content.firstOrNull { it.type == "viewer" }?.viewerId
}

@Serializable
class EpisodeContent(
    val type: String,
    val viewerId: String?,
    val url: String?,
)

@Serializable
class ViewerResponse(
    val result: List<PageDto>,
    val totalPages: Int,
)

@Serializable
class PageDto(
    val imageUrl: String,
    val scramble: String,
    val sort: Int,
)

@Serializable
class UserInfoApiResponse(
    val user: UserData?,
)

@Serializable
class UserData(
    val id: String,
)
