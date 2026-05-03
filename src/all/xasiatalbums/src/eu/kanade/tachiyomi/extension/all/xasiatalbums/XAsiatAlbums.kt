package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class XAsiatAlbums : HttpSource() {
    override val baseUrl = "https://www.xasiat.com/albums"
    override val lang = "all"
    override val name = "XAsiat Albums"
    override val supportsLatest = true

    private val mainUrl = "https://www.xasiat.com"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$mainUrl/")
        .add("X-Requested-With", "XMLHttpRequest")

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)

    override fun latestUpdatesRequest(page: Int) = searchQuery("albums/", "list_albums_common_albums_list", page, mapOf("sort_by" to "post_date"))

    override fun popularMangaRequest(page: Int) = searchQuery("albums/", "list_albums_common_albums_list", page, mapOf("sort_by" to "album_viewed_week"))

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val elements = document.select(".list-albums a, a:has(.thumb)")

        val mangas = elements.distinctBy { it.attr("abs:href") }.map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.attr("title").ifEmpty { element.select(".thumb").attr("alt") }
                thumbnail_url = element.select(".thumb").attr("data-original").ifEmpty { element.select(".thumb").attr("src") }
                status = SManga.COMPLETED
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }

        val hasNextPage = document.selectFirst(".load-more") != null || mangas.size >= 12
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    private fun searchQuery(path: String, blockId: String, page: Int, others: Map<String, String>): Request {
        val offset = (page - 1) * 12 + 1

        return GET(
            mainUrl.toHttpUrl().newBuilder().apply {
                val cleanPath = path.removePrefix("/").removeSuffix("/")
                addPathSegments(cleanPath)
                addQueryParameter("mode", "async")
                addQueryParameter("function", "get_block")
                addQueryParameter("block_id", blockId)
                addQueryParameter("from", offset.toString())
                if (blockId.contains("search")) {
                    addQueryParameter("from_albums", offset.toString())
                }
                others.forEach { addQueryParameter(it.key, it.value) }
                addQueryParameter("_", System.currentTimeMillis().toString())
            }.build(),
            headers,
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val categoryFilter = filterList.firstInstanceOrNull<UriPartFilter>()

        return when {
            query.isNotEmpty() -> searchQuery("search/search/", "list_albums_albums_list_search_result", page, mapOf("q" to query))
            categoryFilter != null && categoryFilter.state > 0 -> {
                searchQuery(categoryFilter.toUriPart(), "list_albums_common_albums_list", page, emptyMap())
            }
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga) = GET(mainUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select(".entry-title").text()
            description = document.select("meta[og:description]").attr("og:description")
            genre = getTags(document).joinToString(", ")
            status = SManga.COMPLETED
        }
    }

    private fun getTags(document: Element): List<String> = document.select(".info-content a").mapNotNull { a ->
        val tag = a.text()
        val href = a.attr("abs:href")
        if (tag.isNotEmpty() && href.contains("/albums/")) {
            val link = href.substringAfter(".com/").removeSuffix("/")
            if (link.isNotEmpty()) categories[tag] = link
            tag
        } else {
            null
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url.encodedPath
        return listOf(
            SChapter.create().apply {
                url = mangaUrl
                name = "Photobook"
                date_upload = System.currentTimeMillis()
            },
        )
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(GET(mainUrl + chapter.url, headers))
        .asObservableSuccess()
        .flatMap { response ->
            val document = response.asJsoup()
            val pageLinks = document.select(".pagination a, .pages a")
                .map { it.attr("abs:href") }
                .filter { it.isNotEmpty() && !it.contains("#") && it.startsWith("http") }
                .distinct()

            if (pageLinks.isEmpty()) {
                Observable.just(pageListParse(document))
            } else {
                val allUrls = (listOf(mainUrl + chapter.url) + pageLinks).distinct()
                val observables = allUrls.map { url ->
                    client.newCall(GET(url, headers)).asObservableSuccess()
                }

                Observable.zip(observables) { responses ->
                    responses.flatMap { res ->
                        pageListParse((res as Response).asJsoup())
                    }
                        .distinctBy { it.imageUrl }
                        .mapIndexed { index, page -> Page(index, "", page.imageUrl) }
                }
            }
        }

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    private fun pageListParse(document: org.jsoup.nodes.Document): List<Page> = document.select("a.item").mapIndexed { i, element ->
        Page(i, imageUrl = element.attr("abs:href"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Multiple pages will be loaded automatically"),
        Filter.Separator(),
        UriPartFilter("Category", categories.entries.map { Pair(it.key, it.value) }.toTypedArray()),
    )
}
