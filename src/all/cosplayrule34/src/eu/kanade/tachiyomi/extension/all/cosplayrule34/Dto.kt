package eu.kanade.tachiyomi.extension.all.cosplayrule34

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PhotosBody(
    @SerialName("album_id") private val albumId: String,
    @SerialName("owner_id") private val ownerId: String,
    private val download: Int,
    private val offset: Int,
    private val limit: Int,
)

@Serializable
class PhotosDto(val photos: List<PhotoDto>)

@Serializable
class PhotoDto(val html: String)
