package eu.kanade.tachiyomi.extension.vi.newtruyentranh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaListResponse(
    val channels: List<MangaChannel> = emptyList(),
    @SerialName("load_more")
    val loadMore: LoadMore? = null,
)

@Serializable
data class MangaChannel(
    val id: String,
    val name: String,
    val description: String = "",
    val image: ImageData,
    @SerialName("remote_data")
    val remoteData: RemoteData,
)

@Serializable
data class ImageData(
    val url: String,
)

@Serializable
data class RemoteData(
    val url: String,
)

@Serializable
data class LoadMore(
    @SerialName("pageInfo")
    val pageInfo: PageInfo? = null,
)

@Serializable
data class PageInfo(
    @SerialName("current_page")
    val currentPage: Int = 1,
    val total: Int = 0,
    @SerialName("per_page")
    val perPage: Int = 24,
    @SerialName("last_page")
    val lastPage: Int = 1,
)

@Serializable
data class MangaDetailResponse(
    val channel: MangaChannel? = null,
    val sources: List<Source> = emptyList(),
)

@Serializable
data class Source(
    val id: String,
    val name: String,
    val contents: List<Content> = emptyList(),
)

@Serializable
data class Content(
    val id: String,
    val name: String = "",
    val streams: List<Stream> = emptyList(),
)

@Serializable
data class Stream(
    val id: String,
    val index: Int,
    val name: String,
    @SerialName("remote_data")
    val remoteData: RemoteData,
)

@Serializable
data class PageListResponse(
    val files: List<PageFile> = emptyList(),
)

@Serializable
class PageFile(
    val id: String,
    val name: String? = null,
    val url: String,
)
