package eu.kanade.tachiyomi.extension.all.e621

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Pool(
    val id: Int,
    val name: String,
    val description: String = "",
    @SerialName("post_ids") val postIds: List<Int> = emptyList(),
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
class PostsResponse(
    val posts: List<Post> = emptyList(),
)

@Serializable
class Post(
    val id: Int,
    val flags: Flags = Flags(),
    val preview: ImageData = ImageData(),
    val sample: ImageData = ImageData(),
    val file: ImageData = ImageData(),
    val tags: Tags = Tags(),
    @SerialName("pools") val poolIds: List<Int> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
    val rating: String = "",
    val score: Score = Score(),
)

@Serializable
class Score(
    val total: Int = 0,
)

@Serializable
class Flags(
    val deleted: Boolean = false,
)

@Serializable
class ImageData(
    val url: String? = null,
    val has: Boolean = true,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
class Tags(
    val general: List<String> = emptyList(),
    val artist: List<String> = emptyList(),
    val copyright: List<String> = emptyList(),
    val character: List<String> = emptyList(),
    val species: List<String> = emptyList(),
    val lore: List<String> = emptyList(),
    val meta: List<String> = emptyList(),
) {
    val allTags: List<String>
        get() = artist + character + copyright + general + lore + meta + species
}

@Serializable
class UserMeResponse(
    @SerialName("blacklisted_tags") private val _blacklistedTags: String? = null,
    private val user: UserMeData? = null,
) {
    val blacklistedTags: String?
        get() = _blacklistedTags ?: user?.blacklistedTags
}

@Serializable
class UserMeData(
    @SerialName("blacklisted_tags") val blacklistedTags: String? = null,
)
