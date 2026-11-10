package eu.kanade.tachiyomi.extension.th.bocchimanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class FeedResponse(val feed: Feed)

@Serializable
class Feed(
    @SerialName("openSearch\$totalResults") private val totalResults: Text,
    @SerialName("openSearch\$startIndex") private val startIndex: Text,
    val entry: List<Entry> = emptyList(),
) {
    val nextStartIndex get() = startIndex.t.toInt() + entry.size

    val hasNextPage get() = entry.isNotEmpty() && nextStartIndex <= totalResults.t.toInt()
}

@Serializable
class Entry(
    private val title: Text,
    private val published: Text,
    private val category: List<Category> = emptyList(),
    private val link: List<Link>,
    @SerialName("media\$thumbnail") private val thumbnail: Thumbnail? = null,
) {
    val isSeries get() = category.any { it.term == SERIES_LABEL }

    private val path get() = link.first { it.rel == "alternate" }.href.toHttpUrl().encodedPath

    fun toSManga() = SManga.create().apply {
        url = path
        title = this@Entry.title.t
        thumbnail_url = thumbnail?.url?.fullSizeImage()
    }

    fun toSChapter() = SChapter.create().apply {
        url = path
        name = title.t.trim()
        date_upload = Instant.tryParse(published.t)
    }
}

@Serializable
class Text(@SerialName("\$t") val t: String)

@Serializable
class Category(val term: String)

@Serializable
class Link(val rel: String, val href: String)

@Serializable
class Thumbnail(val url: String)

@Serializable
class FilterData(
    val genres: List<String>,
    val statuses: List<String>,
    val types: List<String>,
)

const val SERIES_LABEL = "Series"

fun String.fullSizeImage() = replace(IMAGE_SIZE_REGEX, "/s1600/")

private val IMAGE_SIZE_REGEX = Regex("""/s\d+(-[^/]*)?/""")
