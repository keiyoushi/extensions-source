package eu.kanade.tachiyomi.extension.vi.newtruyentranh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaListResponse(
    val channels: List<MangaChannel> = emptyList(),
    @SerialName("load_more")
    val loadMore: LoadMore? = null,
)

@Serializable
class MangaChannel(
    val id: String,
    val name: String,
    val description: String = "",
    val image: ImageData,
    @SerialName("remote_data")
    val remoteData: RemoteData,
)

@Serializable
class ImageData(
    val url: String,
)

@Serializable
class RemoteData(
    val url: String,
)

@Serializable
class LoadMore(
    @SerialName("pageInfo")
    val pageInfo: PageInfo? = null,
)

@Serializable
class PageInfo(
    @SerialName("current_page")
    val currentPage: Int = 1,
    val total: Int = 0,
    @SerialName("per_page")
    val perPage: Int = 24,
    @SerialName("last_page")
    val lastPage: Int = 1,
)

@Serializable
class MangaDetailResponse(
    val channel: MangaChannel? = null,
    val sources: List<Source> = emptyList(),
)

@Serializable
class Source(
    val id: String,
    val name: String,
    val contents: List<Content> = emptyList(),
)

@Serializable
class Content(
    val id: String,
    val name: String? = null,
    val streams: List<Stream> = emptyList(),
)

@Serializable
class Stream(
    val id: String,
    val index: Int,
    val name: String,
    @SerialName("remote_data")
    val remoteData: RemoteData,
)

@Serializable
class PageListResponse(
    val files: List<PageFile> = emptyList(),
)

@Serializable
class PageFile(
    val id: String,
    val name: String? = null,
    val url: String,
)
