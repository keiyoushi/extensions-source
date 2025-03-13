package eu.kanade.tachiyomi.extension.all.danbooru

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Pool(
    val id: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("post_ids") val postIds: List<Int>
)

@Serializable
class Post(
    @SerialName("created_at") val createdAt: String,
    @SerialName("file_url") val fileUrl: String
)
