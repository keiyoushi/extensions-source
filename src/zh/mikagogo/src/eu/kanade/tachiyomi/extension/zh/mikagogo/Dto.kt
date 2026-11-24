package eu.kanade.tachiyomi.extension.zh.mikagogo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PostListResponse(val data: PostList)

// Past the last page, or without any match, the site sends neither the list nor the status
@Serializable
class PostList(
    val html: String? = null,
    @SerialName("starus") private val status: Int = 0,
) {
    val hasNextPage get() = status == 1
}
