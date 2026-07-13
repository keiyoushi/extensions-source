package eu.kanade.tachiyomi.extension.es.zonatmoorg

import android.app.Application
import android.webkit.WebSettings
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class ZonaTmoOrg : HttpSource() {

    override val name = "ZonaTMO"
    override val baseUrl = "https://zonatmo.org"
    override val lang = "es"
    override val supportsLatest = true

    // ──────────────────── Cloudflare / UA ─────────────────────

    private val webViewUserAgent: String? by lazy {
        runCatching { WebSettings.getDefaultUserAgent(Injekt.get<Application>()) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::cloudflareInterceptor)
        .rateLimit(2, 1.seconds)
        .build()

    private fun cloudflareInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (isCloudflareChallenge(response)) {
            response.close()
            CloudflareResolver.resolve(
                loadUrl = baseUrl,
                cookieUrl = baseUrl,
                userAgent = webViewUserAgent,
            )
            return chain.proceed(request)
        }
        return response
    }

    private fun isCloudflareChallenge(response: Response): Boolean {
        if (response.header("cf-ray") == null) return false
        return response.code == 403 || response.code == 404 || response.code == 503
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        add("Referer", "$baseUrl/")
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
        webViewUserAgent?.let { set("User-Agent", it) }
    }

    // ──────────────────── Popular ─────────────────────

    override fun popularMangaRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrl/?tab=populars", headers)
    } else {
        GET("$baseUrl/biblioteca?order_item=likes_count&order_dir=desc&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val requestUrl = response.request.url
        val path = requestUrl.encodedPath

        // Handle homepage tab lists
        if (path == "/" || path.isEmpty()) {
            val tab = requestUrl.queryParameter("tab") ?: "populars"
            val selector = if (tab == "trending") "#pills-trending a[href*=/library/]" else "#pills-populars a[href*=/library/]"
            val elements = document.select(selector)
            val mangas = elements.map { mangaFromElement(it) }
            return MangasPage(mangas, false)
        }

        val elements = document.select("div.element, div.book-item, .element-bg")
        val mangas = elements.map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("ul.pagination li a[rel=next], a:contains(Siguiente), a:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val a = if (element.tagName() == "a") element else (element.selectFirst("a[href*=/library/]") ?: element.selectFirst("a")!!)
        return SManga.create().apply {
            setUrlWithoutDomain(a.attr("href"))
            title = a.selectFirst("h4, .title, h3")?.text()?.trim() ?: a.attr("title").trim()

            // Extract cover image
            val thumbnailElement = element.selectFirst(".thumbnail, .thumb, .book-thumbnail") ?: element
            val styleAttr = thumbnailElement.attr("style").orEmpty()
            val img = element.selectFirst("img")

            val coverUrl = when {
                img != null -> img.absUrl("src").ifEmpty { img.absUrl("data-src") }
                styleAttr.isNotEmpty() -> BACKGROUND_IMAGE_URL_REGEX.find(styleAttr)?.groupValues?.getOrNull(1).orEmpty()
                else -> ""
            }
            thumbnail_url = coverUrl.ifEmpty {
                element.attr("data-bg").ifEmpty {
                    element.selectFirst("[data-bg]")?.attr("data-bg").orEmpty()
                }
            }
        }
    }

    // ──────────────────── Latest ─────────────────────

    override fun latestUpdatesRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrl/?tab=trending", headers)
    } else {
        GET("$baseUrl/biblioteca?order_item=creation&order_dir=desc&page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ──────────────────── Search ─────────────────────

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val realSlug = query.removePrefix(PREFIX_SLUG_SEARCH)
            return client.newCall(GET(baseUrl + realSlug, headers))
                .asObservableSuccess()
                .map { response ->
                    val manga = mangaDetailsParse(response).apply {
                        url = realSlug
                    }
                    MangasPage(listOf(manga), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var homeTab = ""
        var statusVal = ""
        var typeVal = ""
        var demographyVal = ""
        var sortItem = ""
        var sortDir = ""
        val gendersVal = mutableListOf<String>()
        val excludeGendersVal = mutableListOf<String>()

        val filterList = if (filters.isEmpty()) getFilterList() else filters
        for (filter in filterList) {
            when (filter) {
                is HomeTabFilter -> {
                    homeTab = HOME_TABS[filter.state].second
                }
                is StatusFilter -> {
                    statusVal = STATUSES[filter.state].second
                }
                is TypeFilter -> {
                    typeVal = TYPES[filter.state].second
                }
                is DemographyFilter -> {
                    demographyVal = DEMOGRAPHIES[filter.state].second
                }
                is GenreFilter -> {
                    filter.state.forEach { triState ->
                        if (triState.isIncluded()) {
                            gendersVal.add(triState.value)
                        } else if (triState.isExcluded()) {
                            excludeGendersVal.add(triState.value)
                        }
                    }
                }
                is SortFilter -> {
                    filter.state?.let { sort ->
                        sortItem = SORT_COLUMNS[sort.index].second
                        sortDir = if (sort.ascending) "asc" else "desc"
                    }
                }
                else -> {}
            }
        }

        // If page is 1 and a home tab is selected, load the homepage instead
        if (page == 1 && homeTab.isNotEmpty() && query.isEmpty() && gendersVal.isEmpty() && excludeGendersVal.isEmpty() && statusVal.isEmpty() && typeVal.isEmpty() && demographyVal.isEmpty()) {
            return GET("$baseUrl/?tab=$homeTab", headers)
        }

        val url = "$baseUrl/biblioteca".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())
        if (query.isNotEmpty()) {
            url.addQueryParameter("title", query)
        }
        if (statusVal.isNotEmpty()) {
            url.addQueryParameter("status", statusVal)
        }
        if (typeVal.isNotEmpty()) {
            url.addQueryParameter("type", typeVal)
        }
        if (demographyVal.isNotEmpty()) {
            url.addQueryParameter("demography", demographyVal)
        }
        gendersVal.forEach { id ->
            url.addQueryParameter("genders[]", id)
        }
        excludeGendersVal.forEach { id ->
            url.addQueryParameter("exclude_genders[]", id)
        }
        if (sortItem.isNotEmpty()) {
            url.addQueryParameter("order_item", sortItem)
            url.addQueryParameter("order_dir", sortDir)
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        HomeTabFilter(),
        Filter.Separator(),
        SortFilter(),
        Filter.Separator(),
        GenreFilter(),
        Filter.Separator(),
        TypeFilter(),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        DemographyFilter(),
    )

    // ──────────────────── Details ─────────────────────

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1, h2.title")?.text() ?: ""
            description = document.selectFirst(".description, .sinopsis")?.text()
            thumbnail_url = document.selectFirst(".book-thumbnail img, .thumb img")?.absUrl("src")
            author = document.select(".author a").joinToString { it.text() }
            genre = document.select(".genres a, .badge-primary").joinToString { it.text() }
            status = parseStatus(document.selectFirst(".status, .book-status")?.text())
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "publicándose", "en emisión", "ongoing" -> SManga.ONGOING
        "finalizado", "completado", "completed", "terminado", "ended" -> SManga.COMPLETED
        "pausado", "hiatus" -> SManga.ON_HIATUS
        "cancelado", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ──────────────────── Chapters ─────────────────────

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select("ul.chapters-list li, .upload-link, .chapter-list li")

        return elements.mapNotNull { element ->
            val a = element.selectFirst(".chapter-detail a.btn-primary")
                ?: element.selectFirst("a[href*=/view_uploads/]")
                ?: element.selectFirst("a")
                ?: return@mapNotNull null

            val href = a.attr("href")
            if (href == "#" || href.isEmpty()) return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(href)

                val chapterLink = element.selectFirst(".chapter-link, .chapter-number")
                name = chapterLink?.text()?.trim() ?: a.text().trim()

                val numStr = name.replace(Regex("[^0-9.]"), "")
                chapter_number = numStr.toFloatOrNull() ?: -1f
            }
        }
    }

    // ──────────────────── Pages ─────────────────────

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        // 1. Check for img tags directly in viewer
        val imgElements = document.select("#reader-wrap img.reader-image, #reader-wrap img, .reader-image, #viewer img, .viewer img")
        if (imgElements.isNotEmpty()) {
            imgElements.forEachIndexed { i, img ->
                val url = img.absUrl("src").ifEmpty { img.absUrl("data-src") }
                if (url.isNotEmpty()) pages.add(Page(i, "", url))
            }
            return pages.distinctBy { it.imageUrl }
        }

        // 2. Check for JS array (common in TMO)
        val script = document.select("script").find { it.data().contains("var images =") || it.data().contains("images:") }
        if (script != null) {
            val data = script.data()
            val regex = Regex("""(https?://[^"']+\.(png|jpe?g|webp|gif))""")
            regex.findAll(data).forEachIndexed { i, matchResult ->
                pages.add(Page(i, "", matchResult.value))
            }
        }

        return pages.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"
        private val BACKGROUND_IMAGE_URL_REGEX = Regex("""background-image\s*:\s*url\(['\"]?([^'\")]+)['\"]?\)""")
    }
}
