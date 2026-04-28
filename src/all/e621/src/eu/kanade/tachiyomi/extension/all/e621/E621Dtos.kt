package eu.kanade.tachiyomi.extension.all.e621

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Pool(
    val id: Int,
    val name: String,
    val description: String = "",
    @SerialName("post_ids") val postIds: List<Int> = emptyList(),
    @SerialName("is_active") val isActive: Boolean? = null,
    val category: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class PostsResponse(
    val posts: List<Post> = emptyList(),
)

@Serializable
data class Post(
    val id: Int,
    val flags: Flags = Flags(),
    val preview: ImageData = ImageData(),
    val sample: ImageData = ImageData(),
    val file: ImageData = ImageData(),
    val tags: Tags = Tags(),
)

@Serializable
data class Flags(
    val deleted: Boolean = false,
)

@Serializable
data class ImageData(
    val url: String? = null,
)

@Serializable
data class Tags(
    val artist: List<String> = emptyList(),
)
