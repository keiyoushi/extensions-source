package eu.kanade.tachiyomi.extension.en.characterdesignreferences

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser

@Serializable
class SearchResponse(val totalCount: Int, val items: List<SearchItem>)

@Serializable
class SearchItem(private val title: String, val itemUrl: String, private val imageUrl: String? = null) {
    fun toSManga() = SManga.create().apply {
        url = itemUrl
        title = this@SearchItem.title.unescapeTitle()
        // Search image urls end in a slash the listing urls don't have
        thumbnail_url = imageUrl?.removeSuffix("/")?.let(::coverUrl)
    }
}

@Serializable
class ItemResponse(val item: Item)

@Serializable
class Item(
    val title: String,
    val assetUrl: String? = null,
    val publishOn: Long,
    val body: String,
    val tags: List<String>,
    val categories: List<String>,
)

// Squarespace's own resized copy; covers are uploaded at up to 2500 px
fun coverUrl(url: String) = url.toHttpUrl().newBuilder().setQueryParameter("format", "750w").build().toString()

// Post titles come HTML-escaped and sometimes space-padded
fun String.unescapeTitle(): String = Parser.unescapeEntities(this, false).trim()
