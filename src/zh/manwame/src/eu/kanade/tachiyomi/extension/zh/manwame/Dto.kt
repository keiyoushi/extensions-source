package eu.kanade.tachiyomi.extension.zh.manwame

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ListingDto(
    @SerialName("has_more") val hasMore: Boolean,
    val rows: List<ComicDto>,
)

@Serializable
class ComicDto(
    private val url: String,
    private val name: String,
    private val image: String,
    private val author: String,
    private val state: Int,
    @SerialName("theme_array") private val themes: List<ThemeDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = this@ComicDto.url
        title = name
        thumbnail_url = image.ifEmpty { null }
        author = this@ComicDto.author.takeIf { it.isNotEmpty() && it != "未知" }
        genre = themes.joinToString { it.name }.ifEmpty { null }
        status = when (state) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ThemeDto(val name: String)

@Serializable
class ChapterDataDto(@SerialName("chapter_images") val images: List<String>)
