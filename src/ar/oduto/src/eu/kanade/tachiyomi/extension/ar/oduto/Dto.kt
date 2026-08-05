package eu.kanade.tachiyomi.extension.ar.oduto
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class BloggerFeedResponse(
    val feed: BloggerFeed,
)

@Serializable
class BloggerFeed(
    val entry: List<BloggerEntry>,
)

@Serializable
class BloggerEntry(
    private val title: TextField,
    private val published: TextField,
    private val link: List<BloggerLink>,
    private val author: List<BloggerAuthor>,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = title.text.trim() + "\u200F"
        url = link.first { it.rel == "alternate" }.href.toHttpUrl().encodedPath
        date_upload = Instant.parseOrNull(published.text)?.toEpochMilliseconds() ?: 0L
        scanlator = author.firstOrNull()?.name?.text
    }
}

@Serializable
class BloggerLink(
    val rel: String,
    val href: String,
)

@Serializable
class BloggerAuthor(
    val name: TextField,
)

@Serializable
class TextField(
    @SerialName("\$t") val text: String,
)
