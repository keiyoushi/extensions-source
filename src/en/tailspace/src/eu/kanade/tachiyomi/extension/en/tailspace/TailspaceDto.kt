package eu.kanade.tachiyomi.extension.en.tailspace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class BrowsePageDto(
    @SerialName("data") val data: BrowseDataDto? = null,
)

@Serializable
class BrowseDataDto(
    @SerialName("comicsAndAds") val comicsAndAds: List<BrowseComicDto>? = null,
    @SerialName("numberOfPages") val numberOfPages: Int = 1,
)

@Serializable
class BrowseComicDto(
    @SerialName("id") val id: JsonPrimitive? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("thumbnailVersion") val thumbnailVersion: Int? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("isSfw") val isSfw: Boolean? = null,
    @SerialName("tags") val tags: List<TagItemDto>? = null,
)

@Serializable
class ComicPageDto(
    @SerialName("data") val data: ComicDataDto? = null,
)

@Serializable
class ComicDataDto(
    @SerialName("comic") val comic: ComicDetails? = null,
)

@Serializable
class ComicDetails(
    @SerialName("id") val id: Long = 0L,
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("isSfw") val isSfw: Boolean? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("thumbnailVersion") val thumbnailVersion: Int? = null,
    @SerialName("artist") val artist: ArtistDto? = null,
    @SerialName("additionalArtists") val additionalArtists: List<ArtistDto>? = null,
    @SerialName("tags") val tags: List<TagItemDto>? = null,
    @SerialName("pages") val pages: List<PageItem>? = null,
    @SerialName("updated") val updated: Long? = null,
    @SerialName("published") val published: Long? = null,
    @SerialName("previousComic") val previousComic: LinkedComic? = null,
    @SerialName("nextComic") val nextComic: LinkedComic? = null,
) {
    val artists: String?
        get() = buildList {
            artist?.name?.let { add(it) }
            additionalArtists.orEmpty().forEach { artistElem ->
                artistElem.name?.let { add(it) }
            }
        }.distinct().joinToString(", ").ifEmpty { null }
}

@Serializable
class PageItem(
    @SerialName("token") val token: String,
    @SerialName("pageNumber") val pageNumber: Int = 0,
)

@Serializable
class LinkedComic(
    @SerialName("id") val id: Long = 0L,
    @SerialName("name") val name: String? = null,
)

@Serializable
class ArtistDto(
    @SerialName("name") val name: String? = null,
)

@Serializable
class TagItemDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("category") val category: String? = null,
)

@Serializable
class TagsResponseDto(
    @SerialName("data") val data: List<TagDto>? = null,
)

@Serializable
class TagDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("category") val category: String? = null,
)
