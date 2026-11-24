package eu.kanade.tachiyomi.extension.all.hentainame

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.RandomEntryFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.toDate
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class HentaiName : GalleryAdults() {

    // Galleries are only listed by date: popularity is a sort of search results
    override val supportsLatest = false

    override val popularMangaUrl get() = "$baseUrl/new/"

    override val latestUpdatesUrl get() = "$baseUrl/new/"

    override fun String.addPageUri(page: Int) = "$this${if ('?' in this) "&" else "?"}p=$page"

    override fun popularMangaSelector() = "div.gallery"

    override fun popularMangaNextPageSelector() = "a.next"

    override fun Element.mangaUrl() = selectFirst("a.cover")?.absUrl("href")

    override fun Element.mangaThumbnail() = selectFirst("img")?.imgAttr()

    override val idPrefixUri = "g"

    // Gallery URLs without the trailing slash answer 404
    override suspend fun getMangaById(id: String): SManga = parseMangaDetails(client.get("$baseUrl/g/$id/", Fetch.DOCUMENT).asJsoup())
        .apply { url = "/g/$id/" }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "g") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.toIntOrNull() != null } ?: return null
        return getMangaById(id)
    }

    // The site's search form redirects to /search/<query>/, with whitespace turned into dashes
    override fun basicSearchUrl(page: Int, query: String, filters: FilterList): String {
        val sort = getSortOrderURIs()[filters.firstInstanceOrNull<SortOrderFilter>()?.state ?: 0].second
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment(query.trim().lowercase().replace(WHITESPACE, "-"))
            .addPathSegment("")
            .apply { if (sort.isNotEmpty()) addQueryParameter("sort", sort) }
            .build()
            .toString()
    }

    override val mangaDetailInfoSelector = "#bigcontainer"

    override fun Element.getCover() = selectFirst("#cover img")?.imgAttr()

    override fun getInfoSelector(tag: String) = "#info .field-name:containsOwn($tag:) .tags a"

    override fun Element.totalPages() = selectFirst("#info > div:matchesOwn(^\\d+ pages?$)")?.ownText()?.substringBefore(' ').orEmpty()

    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // The datetime attribute holds the same placeholder on every gallery, the text is the upload date
    override fun Element.getTime() = selectFirst("#info time")?.text().toDate(dateFormat)

    // Gallery pages list 100 thumbnails at most, but every page is stored next to the cover as <n>.webp
    override suspend fun pageListParse(document: Document): List<Page> {
        val imagesUrl = document.getCover()!!.substringBeforeLast('/')
        return (1..document.totalPages().toInt()).map { Page(it - 1, imageUrl = "$imagesUrl/$it.webp") }
    }

    override val supportsFilterFetching = false

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Sorting applies to text search"),
        SortOrderFilter(getSortOrderURIs()),
        RandomEntryFilter(),
    )

    override fun getSortOrderURIs() = listOf(
        "Relevance" to "",
        "Date" to "date",
        "Popular" to "popular",
    )

    companion object {
        private val WHITESPACE = Regex("""\s+""")
    }
}
