package eu.kanade.tachiyomi.extension.all.cosplaytele

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class CosplayTele : HttpSource() {

    override val name = "CosplayTele"

    override val baseUrl = "https://cosplaytele.com"

    override val lang = "all"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val popularPageLimit = 20

    private var categories = CATEGORIES.toMap().toMutableMap()

    private var filtersState = FilterState.Unfetched
    private var filterAttempts = 0

    private enum class FilterState {
        Fetching,
        Fetched,
        Unfetched,
    }

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/wp-json/wordpress-popular-posts/v1/popular-posts".toHttpUrl().newBuilder()
            .addQueryParameter("offset", (page * popularPageLimit).toString())
            .addQueryParameter("limit", popularPageLimit.toString())
            .addQueryParameter("range", "last7days")
            .addQueryParameter("embed", "true")
            .addQueryParameter("_embed", "wp:featuredmedia")
            .addQueryParameter("_fields", "title,link,_embedded,_links.wp:featuredmedia")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<PopularPostDto>>()
        val mangas = result.map { item ->
            SManga.create().apply {
                title = item.title.rendered
                setUrlWithoutDomain(item.link)
                thumbnail_url = item.embedded?.featuredMedia?.getOrNull(0)?.sourceUrl
            }
        }
        return MangasPage(mangas, mangas.size >= popularPageLimit)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ========================= Search =========================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("http")) {
            val url = query.toHttpUrlOrNull()
            if (url != null && (url.host == "cosplaytele.com" || url.host == "www.cosplaytele.com")) {
                val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
                if (pathSegments.isEmpty()) return super.fetchSearchManga(page, query, filters)

                return if (pathSegments[0] == "category" || pathSegments[0] == "tag") {
                    val paginatedUrl = url.newBuilder().apply {
                        val pageIndex = url.pathSegments.indexOf("page")
                        if (pageIndex != -1) {
                            setPathSegment(pageIndex + 1, page.toString())
                        } else {
                            addPathSegment("page")
                            addPathSegment(page.toString())
                        }
                    }.build()
                    client.newCall(GET(paginatedUrl, headers)).asObservableSuccess().map { response ->
                        searchMangaParse(response)
                    }
                } else {
                    client.newCall(GET(query, headers)).asObservableSuccess().map { response ->
                        val manga = mangaDetailsParse(response).apply {
                            setUrlWithoutDomain(query)
                        }
                        MangasPage(listOf(manga), false)
                    }
                }
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val categoryFilter = filters.firstInstanceOrNull<UriPartFilter>()

        return when {
            categoryFilter != null && categoryFilter.state != 0 -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegments(categoryFilter.toUriPart())
                    addPathSegment("page")
                    addPathSegment(page.toString())
                    if (query.isNotEmpty()) {
                        addQueryParameter("s", query)
                    }
                }.build()
                GET(url, headers)
            }
            query.isNotEmpty() -> {
                val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
                    .addQueryParameter("s", query)
                    .build()
                GET(url, headers)
            }
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("main div.box").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                val linkEl = element.selectFirst("h5 a") ?: throw Exception("Title is mandatory")
                title = linkEl.text()
                setUrlWithoutDomain(linkEl.attr("abs:href"))
            }
        }
        val hasNextPage = document.selectFirst(".next.page-number") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Details =========================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".entry-title")?.text() ?: throw Exception("Title is mandatory")
            description = title
            genre = getTags(document).joinToString(", ")
            status = SManga.COMPLETED
        }
    }

    private fun getTags(document: Element): List<String> = document.select("#main a").filter { a ->
        TAG_PATTERN.matches(a.attr("abs:href"))
    }.map { a ->
        val tag = a.text()
        if (tag.isNotEmpty()) {
            val link = a.attr("abs:href").substringAfter(baseUrl).removePrefix("/")
            categories[tag] = link
        }
        tag
    }

    // ========================= Chapters =========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Gallery"
                url = response.request.url.encodedPath
                date_upload = document.selectFirst("time.updated")?.attr("datetime")?.let { getDate(it) } ?: 0L
            },
        )
    }

    private fun getDate(dateStr: String): Long = DATE_FORMAT.tryParse(dateStr.substringBefore("T"))

    // ========================= Pages =========================

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select(".gallery-item img").mapIndexed { i, element ->
        Page(i, imageUrl = element.attr("abs:src"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Filters =========================

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch { fetchFilters() }
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("NOTE: Only one filter will be applied!"),
            Filter.Separator(),
            UriPartFilter("Category", categories.entries.map { it.key to it.value }.toTypedArray()),
        )

        if (filtersState == FilterState.Unfetched) {
            filters.add(1, Filter.Header("Use 'reset' to load all filters"))
        }
        return FilterList(filters)
    }

    private suspend fun fetchFilters() {
        if (filtersState == FilterState.Unfetched && filterAttempts < 3) {
            filtersState = FilterState.Fetching
            filterAttempts++

            try {
                client.newCall(GET("$baseUrl/explore-categories/", headers))
                    .await()
                    .asJsoup().let { document -> getTags(document) }
                filtersState = FilterState.Fetched
            } catch (e: Exception) {
                Log.e(name, e.stackTraceToString())
                filtersState = FilterState.Unfetched
            }
        }
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }

        private val TAG_PATTERN = """.*/(tag|category)/.*""".toRegex()
    }
}
