package eu.kanade.tachiyomi.extension.all.jjcos

import kotlinx.serialization.Serializable

@Serializable
class IndexDto(
    val posts: List<PostDto>,
)

@Serializable
class PostDto(
    val title: String,
    val link: String,
    val feature: String? = null,
    val content: String? = null,
    val dateFormat: String? = null,
)
