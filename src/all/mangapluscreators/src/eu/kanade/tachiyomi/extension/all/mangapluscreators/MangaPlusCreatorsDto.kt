package eu.kanade.tachiyomi.extension.all.mangapluscreators

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MpcResponse(
    val status: String,
    val titles: List<MpcTitle>? = null,
)

@Serializable
data class MpcTitle(
    val title: String,
    val thumbnail: String,
    @SerialName("is_one_shot") val isOneShot: Boolean,
    val author: MpcAuthorDto,
    @SerialName("latest_episode") val latestEpisode: MpcLatestEpisode,
)

@Serializable
data class MpcAuthorDto(
    val name: String,
)

@Serializable
data class MpcLatestEpisode(
    @SerialName("title_connect_id") val titleConnectId: String,
)

@Serializable
data class MpcReaderDataPages(
    val pc: List<MpcReaderPage>,
)

@Serializable
data class MpcReaderPage(
    @SerialName("page_no") val pageNo: Int,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
data class MpcReaderDataTitle(
    val title: String,
    val thumbnail: String,
    @SerialName("is_oneshot") val isOneShot: Boolean,
    @SerialName("contents_id") val contentsId: String,
)

data class ChaptersPage(val chapters: List<SChapter>, val hasNextPage: Boolean)
