package eu.kanade.tachiyomi.extension.all.danbooru

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Pool(
    val id: Int,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("post_ids") val postIds: List<Int>,
)

@Serializable
class Post(
    @SerialName("file_url") val fileUrl: String,
)
