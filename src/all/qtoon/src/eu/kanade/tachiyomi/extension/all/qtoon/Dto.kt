package eu.kanade.tachiyomi.extension.all.qtoon

import kotlinx.serialization.Serializable

@Serializable
class EncryptedResponse(
    val ts: Long,
    val data: String,
)

@Serializable
class Comics(
    val comics: List<Comic>,
    val more: Int,
)

@Serializable
class Comic(
    val csid: String,
    val webLinkId: String? = null,
    val title: String,
    val image: Image,
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
    val comic: ComicDetails,
)

@Serializable
class ComicDetails(
    val csid: String,
    val webLinkId: String? = null,
    val title: String,
    val image: Image,
    val tags: List<Tag>,
    val author: String? = null,
    val serialStatus: String,
    val updateMemo: String? = null,
    val introduction: String,
    val corners: Corner,
)

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
