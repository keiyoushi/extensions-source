package eu.kanade.tachiyomi.extension.all.luscious

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Info(
    @SerialName("has_next_page")
    val hasNextPage: Boolean,
)

@Serializable
class SingleIdVariable(
    val id: String,
)

// ALBUM LIST
@Serializable
class AlbumListResponse(
    val data: AlbumListData,
)

@Serializable
class AlbumListData(
    val album: AlbumListDataAlbum,
)

@Serializable
class AlbumListDataAlbum(
    val list: AlbumList,
)

@Serializable
class AlbumList(
    val info: Info,
    val items: List<Album>,
)

// ALBUM
@Serializable
class Album(
    val url: String,
    val title: String,
    val cover: Cover,
)

@Serializable
class FullAlbum(
    val url: String,
    val title: String,
    val cover: Cover,
    val description: String,
    val language: ItemWithTitle?,
    val labels: List<String>,
    val genres: List<ItemWithTitle>,
    val audiences: List<ItemWithTitle>,
    val tags: List<Tag>,
    @SerialName("number_of_pictures")
    val numberOfPictures: Int,
    @SerialName("number_of_animated_pictures")
    val numberOfAnimatedPictures: Int,
    val content: ItemWithTitle,
)

@Serializable
class ItemWithTitle(
    val title: String,
)

@Serializable
class Tag(
    val text: String,
)

@Serializable
class Cover(
    val url: String,
    val height: Int,
    val width: Int,
)

// QUERY
@Serializable
class Variables(
    val input: Input,
)

@Serializable
class Input(
    val display: String?,
    val page: Int,
    val filters: List<Filter>,
)

@Serializable
class Filter(
    val name: String,
    val value: String,
)

// AlbumListOwnPictures

@Serializable
class AlbumListOwnPicturesResponse(
    val data: AlbumListOwnPicturesData,
)

@Serializable
class AlbumListOwnPicturesData(
    val picture: AlbumListOwnPicturesDataPicture,
)

@Serializable
class AlbumListOwnPicturesDataPicture(
    val list: AlbumListOwnPicturesList,
)

@Serializable
class AlbumListOwnPicturesList(
    val info: Info,
    val items: List<Picture>,
)

@Serializable
class Picture(
    val thumbnails: List<Cover>,
    @SerialName("url_to_original")
    val urlToOriginal: String?,
    @SerialName("url_to_video")
    val urlToVideo: String?,
    val position: Int,
    val title: String,
    val created: Double,
)

class PictureItem(
    val index: Int,
    val url: String,
    val title: String? = null,
    val created: Long? = null,
)

// AlbumGet

@Serializable
class AlbumGetResponse(
    val data: AlbumGetData,
)

@Serializable
class AlbumGetData(
    val album: AlbumGetDataGet,
)

@Serializable
class AlbumGetDataGet(
    val get: FullAlbum,
)
