package eu.kanade.tachiyomi.extension.all.jjcos

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.io.IOException

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
) {
    fun toSManga(encodedPath: String): SManga {
        val mangaTitle = title.trim()
        if (mangaTitle.isEmpty()) {
            throw IOException("Missing title in $link")
        }

        return SManga.create().apply {
            title = mangaTitle
            thumbnail_url = feature
            status = SManga.COMPLETED
            url = encodedPath
            initialized = true
        }
    }
}
