package eu.kanade.tachiyomi.extension.pt.bendezomnihero

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class BloggerFeedResponse(val feed: BloggerFeed)

@Serializable
class BloggerFeed(val entry: List<BloggerEntry> = emptyList())

@Serializable
class BloggerText(@SerialName("\$t") val text: String)

@Serializable
class BloggerLink(
    private val rel: String,
    private val href: String,
) {
    fun isAlternate(): Boolean = rel == "alternate"

    fun href(): String = href
}

@Serializable
class BloggerEntry(
    val title: BloggerText,
    val content: BloggerText? = null,
    val published: BloggerText? = null,
    val link: List<BloggerLink> = emptyList(),
) {
    fun feedTitle(): String = title.text

    fun postPath(): String = link.first(BloggerLink::isAlternate).href().toHttpUrl().encodedPath

    fun publishedEpoch(): Long = Instant.tryParse(published?.text)

    fun isReadableComic(): Boolean = inlineImageCount() >= MIN_INLINE_IMAGES

    fun firstImageUrl(): String? = content?.text?.let { firstImageRegex.find(it)?.groupValues?.get(1) }

    fun toPostManga(): SManga = SManga.create().apply {
        url = postPath()
        title = feedTitle().ifBlank { error("Post at ${postPath()} has no title") }
        thumbnail_url = firstImageUrl()
        status = SManga.COMPLETED
    }

    private fun inlineImageCount(): Int = content?.text?.let { inlineImageRegex.findAll(it).count() } ?: 0

    companion object {
        private const val MIN_INLINE_IMAGES = 20
        private val inlineImageRegex =
            Regex("""<img[^>]+?src="https://blogger\.googleusercontent\.com[^"]*"""")
        private val firstImageRegex =
            Regex("""<img[^>]+?src="(https://blogger\.googleusercontent\.com[^"]*)"""")
    }
}
