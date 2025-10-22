package eu.kanade.tachiyomi.extension.all.qtoon

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonString
import kotlinx.serialization.Serializable

@Serializable
class EncryptedResponse(
    val ts: Long,
    val data: String,
)

@Serializable
class ComicsList(
    val comics: List<Comic>,
    val more: Int,
)

@Serializable
class ComicUrl(
    val csid: String,
    val webLinkId: String,
)

@Serializable
class Image(
    val thumb: Thumb,
)

@Serializable
class Thumb(
    val url: String,
)

@Serializable
class ComicDetailsResponse(
    val comic: Comic,
)

@Serializable
class Comic(
    val csid: String,
    val webLinkId: String? = null,
    val title: String,
    val image: Image,
    val tags: List<Tag>,
    val author: String? = null,
    val serialStatus2: Int,
    val updateMemo: String? = null,
    val introduction: String,
    val corners: Corner,
) {
    fun toSManga() = SManga.create().apply {
        url = ComicUrl(csid, webLinkId.orEmpty()).toJsonString()
        title = this@Comic.title
        thumbnail_url = image.thumb.url
        author = this@Comic.author
        description = buildString {
            append(introduction)
            if (!updateMemo.isNullOrBlank()) {
                append("\n\nUpdates: ", updateMemo)
            }
        }
        genre = buildSet {
            tags.mapTo(this) { it.name }
            corners.cornerTags.mapTo(this) { it.name }
        }.joinToString()
        status = when (serialStatus2) {
            101 -> SManga.ONGOING
            103 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class Tag(
    val name: String,
)

@Serializable
class Corner(
    val cornerTags: List<Tag>,
)

@Serializable
class ChapterEpisodes(
    val episodes: List<Episode>,
)

@Serializable
class Episode(
    val esid: String,
    val title: String,
    val serialNo: Int,
)

@Serializable
class EpisodeUrl(
    val esid: String,
    val csid: String,
)

@Serializable
class EpisodeResponse(
    val definitions: List<EpisodeDefinition>,
)

@Serializable
class EpisodeDefinition(
    val token: String,
)

@Serializable
class EpisodeResources(
    val resources: List<Resource>,
    val more: Int,
)

@Serializable
class Resource(
    val url: String,
    val rgIdx: Int,
)
