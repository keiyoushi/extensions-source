package eu.kanade.tachiyomi.extension.es.sinergiasincontrol

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class FeedResponseDto(val feed: FeedDto)

@Serializable
class FeedDto(
    @SerialName("openSearch\$totalResults") private val total: TextDto,
    val entry: List<PostDto>,
) {
    val totalResults get() = total.t.toInt()
}

@Serializable
class PostDto(
    private val title: TextDto,
    private val published: TextDto,
    private val content: TextDto,
    private val link: List<LinkDto>,
) {
    val hasImage get() = "<img" in content.t

    fun toSChapter(number: Int) = SChapter.create().apply {
        url = link.first { it.rel == "alternate" }.href.toHttpUrl().encodedPath
        // The blog shows only the date of its few untitled posts
        name = title.t.trim().ifEmpty { published.t.substringBefore("T") }
        date_upload = Instant.tryParse(published.t)
        chapter_number = number.toFloat()
    }
}

@Serializable
class TextDto(@SerialName("\$t") val t: String)

@Serializable
class LinkDto(val rel: String, val href: String)
