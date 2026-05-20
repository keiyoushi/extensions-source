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
    // @SerialName("creator_name") val creatorName: String = "",
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
    @SerialName("pools") val poolIds: List<Int> = emptyList(),
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val rating: String = "",
    val score: Score = Score(),
)

@Serializable
data class Score(
    val up: Int = 0,
    val down: Int = 0,
    val total: Int = 0,
)

@Serializable
data class Flags(
    val deleted: Boolean = false,
)

@Serializable
data class ImageData(
    val url: String? = null,
    val has: Boolean = true,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class Tags(
    val general: List<String> = emptyList(),
    val artist: List<String> = emptyList(),
    val copyright: List<String> = emptyList(),
    val character: List<String> = emptyList(),
    val species: List<String> = emptyList(),
    val lore: List<String> = emptyList(),
)
