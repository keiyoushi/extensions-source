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
    @SerialName("file_url") private val fileUrl: String? = null,
    @SerialName("large_file_url") private val largeFileUrl: String? = null,
    @SerialName("preview_file_url") private val previewFileUrl: String? = null,
) {
    val bestUrl: String
        get() = fileUrl ?: largeFileUrl ?: previewFileUrl ?: throw Exception("Image URL not found for post")
}
