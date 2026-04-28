package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class XAsiatAlbums : HttpSource() {
    override val baseUrl = "https://www.xasiat.com/albums"
    override val lang = "all"
    override val name = "XAsiat Albums"
    override val supportsLatest = true

    private val mainUrl = "https://www.xasiat.com"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)

    // Latest
    override fun latestUpdatesRequest(page: Int) = searchQuery("albums/", "list_albums_common_albums_list", page, mapOf("sort_by" to "post_date"))

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Popular
    override fun popularMangaRequest(page: Int) = searchQuery("albums/", "list_albums_common_albums_list", page, mapOf("sort_by" to "album_viewed_week"))

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".list-albums a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.attr("title")
                thumbnail_url = element.select(".thumb").attr("data-original")
                status = SManga.COMPLETED
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }
        val hasNextPage = document.selectFirst(".load-more") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchQuery(path: String, blockId: String, page: Int, others: Map<String, String>): Request = GET(
        mainUrl.toHttpUrl().newBuilder().apply {
            addPathSegments(path)
            addQueryParameter("mode", "async")
            addQueryParameter("function", "get_block")
            addQueryParameter("block_id", blockId)
            addQueryParameter("from", page.toString())
            others.forEach { addQueryParameter(it.key, it.value) }
            addQueryParameter("_", System.currentTimeMillis().toString())
        }.build(),
        headers,
    )

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val categoryFilter = filterList.firstInstanceOrNull<UriPartFilter>()

        return when {
            query.isNotEmpty() -> searchQuery("search/search/", "list_albums_albums_list_search_result", page, mapOf("q" to query, "from_albums" to page.toString()))
            categoryFilter != null && categoryFilter.state != 0 -> searchQuery(categoryFilter.toUriPart(), "list_albums_common_albums_list", page, mapOf("q" to query))
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsRequest(manga: SManga) = GET("${mainUrl}${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select(".entry-title").text()
            description = document.select("meta[og:description]").attr("og:description")
            genre = getTags(document).joinToString(", ")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    private fun getTags(document: Element): List<String> = document.select(".info-content a").mapNotNull { a ->
        val tag = a.text()
        if (tag.isNotEmpty()) {
            val href = a.attr("abs:href")
            val link = href.substringAfter(".com/", "")
            if (link.isNotEmpty()) {
                categories[tag] = link
            }
            tag
        } else {
            null
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val thumbUrl = manga.thumbnail_url.orEmpty().ifEmpty { baseUrl }
        return Request.Builder()
            .url("$thumbUrl#${manga.url}")
            .head()
            .headers(headers)
            .build()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val lastModified = response.headers["last-modified"]
        val mangaUrl = response.request.url.fragment ?: response.request.url.encodedPath

        return listOf(
            SChapter.create().apply {
                url = mainUrl + mangaUrl
                name = "Photobook"
                date_upload = dateFormat.tryParse(lastModified)
            },
        )
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("a.item").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:href"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        UriPartFilter("Category", categories.entries.map { Pair(it.key, it.value) }.toTypedArray()),
    )
}
