package eu.kanade.tachiyomi.extension.all.mangapluscreators

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MpcResponse(
    val status: String,
    val titles: List<MpcTitle>? = null,
)

@Serializable
class MpcTitle(
    val title: String,
    val thumbnail: String,
    @SerialName("is_one_shot") val isOneShot: Boolean,
    val author: MpcAuthorDto,
    @SerialName("latest_episode") val latestEpisode: MpcLatestEpisode,
)

@Serializable
class MpcAuthorDto(
    val name: String,
)

@Serializable
class MpcLatestEpisode(
    @SerialName("title_connect_id") val titleConnectId: String,
)

@Serializable
class MpcReaderDataPages(
    val pc: List<MpcReaderPage>,
)

@Serializable
class MpcReaderPage(
    @SerialName("page_no") val pageNo: Int,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
class MpcReaderDataTitle(
    val title: String,
    val thumbnail: String,
    @SerialName("is_oneshot") val isOneShot: Boolean,
    @SerialName("contents_id") val contentsId: String,
)

class ChaptersPage(val chapters: List<SChapter>, val hasNextPage: Boolean)
