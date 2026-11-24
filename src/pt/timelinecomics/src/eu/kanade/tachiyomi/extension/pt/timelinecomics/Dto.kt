package eu.kanade.tachiyomi.extension.pt.timelinecomics

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class FeedResponseDto(val feed: FeedDto)

@Serializable
class FeedDto(val entry: List<PostDto> = emptyList())

@Serializable
class PostDto(
    private val title: TextDto,
    private val link: List<LinkDto>,
    @SerialName("media\$thumbnail") private val thumbnail: ThumbnailDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = link.first { it.rel == "alternate" }.href.toHttpUrl().encodedPath
        title = this@PostDto.title.t.trim()
        thumbnail_url = thumbnail?.url?.let(::coverUrl)
    }
}

@Serializable
class TextDto(@SerialName("\$t") val t: String)

@Serializable
class LinkDto(val rel: String, val href: String)

@Serializable
class ThumbnailDto(val url: String)

// Blogger serves an image at the size written before its file name, like the 72 px squares of the feed and widgets
fun coverUrl(url: String) = url.replace(IMAGE_SIZE_REGEX, "/w600/")

private val IMAGE_SIZE_REGEX = Regex("""/[sw]\d+[^/]*/(?=[^/]+$)""")
