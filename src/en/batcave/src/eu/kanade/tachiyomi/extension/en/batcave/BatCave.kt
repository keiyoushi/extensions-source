package eu.kanade.tachiyomi.extension.en.batcave

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class BatCave : HttpSource() {

    override val name = "BatCave"
    override val lang = "en"
    override val supportsLatest = true
    override val baseUrl = "https://batcave.biz"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Use cloudflareClient to sync cookies with WebView and intercept the DLE Guard redirect.
    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Detect if the site redirected us to the anti-bot challenge page
            if (response.request.url.pathSegments.firstOrNull() == "_c") {
                response.close()
                throw IOException("Please open in WebView to bypass site protection")
            }
            response
        }
        .build()

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.POPULAR)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.LATEST)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = buildString {
                append(baseUrl)
                append("/search/")
                append(URLEncoder.encode(query.trim(), "UTF-8"))
                if (page > 1) {
                    append("/page/")
                    append(page)
                    append("/")
                }
            }
            return GET(url, headers)
        }

        var filtersApplied = false
        val filterPath = buildString {
            filters.firstInstanceOrNull<YearFilter>()?.addFilterToUrl(this)?.also { filtersApplied = true }
            filters.firstInstanceOrNull<PublisherFilter>()?.addFilterToUrl(this)?.also { filtersApplied = true }
            filters.firstInstanceOrNull<GenreFilter>()?.addFilterToUrl(this)?.also { filtersApplied = true }
        }

        val url = buildString {
            append(baseUrl)
            if (filtersApplied) {
                append("/ComicList/")
                append(filterPath)
            } else {
                append("/comix/")
            }
            if (page > 1) {
                append("page/")
                append(page)
                append("/")
            }
        }

        val sort = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()

        return if (sort.getSort().isEmpty()) {
            GET(url, headers)
        } else {
            val form = FormBody.Builder().apply {
                add("dlenewssortby", sort.getSort())
                add("dledirection", sort.getDirection())
                if (filtersApplied) {
                    add("set_new_sort", "dle_sort_xfilter")
                    add("set_direction_sort", "dle_direction_xfilter")
                } else {
                    add("set_new_sort", "dle_sort_cat_1")
                    add("set_direction_sort", "dle_direction_cat_1")
                }
            }.build()

            POST(url, headers, form)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (response.request.url.pathSegments.firstOrNull() != "search") {
            parseFilters(document)
        }

        val entries = document.select("#dle-content > .readed").map { element ->
            SManga.create().apply {
                with(element.selectFirst(".readed__title > a")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    title = ownText()
                }
                thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst("div.pagination__pages")
            ?.children()?.last()?.tagName() == "a"

        return MangasPage(entries, hasNextPage)
    }

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("header.page__header h1")!!.text()
            thumbnail_url = document.selectFirst("div.page__poster img")?.absUrl("src")
            description = document.selectFirst("div.page__text")?.text()
            author = document.selectFirst(".page__list > li:has(> div:contains(Writer))")?.ownText()
            artist = document.selectFirst(".page__list > li:has(> div:contains(Artist))")?.ownText()
            genre = buildList {
                document.select("div.page__tags a").mapTo(this) { it.text() }
                add("Comic")
            }.joinToString()
            status = when (document.selectFirst(".page__list > li:has(> div:contains(Release type))")?.ownText()?.trim()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: throw Exception("Chapter data script not found")

        val data = script
            .substringAfter("window.__DATA__ = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<Chapters>()

        return data.chapters.map { chap ->
            SChapter.create().apply {
                url = "/reader/${data.comicId}/${chap.id}${data.xhash}"
                name = chap.title
                chapter_number = chap.number
                date_upload = dateFormat.tryParse(chap.date)
            }
        }
    }

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: throw Exception("Page data script not found")

        val data = script
            .substringAfter("window.__DATA__ = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<Images>()

        return data.images.mapIndexed { idx, img ->
            val imageUrl = if (img.startsWith("http")) img.trim() else baseUrl + img.trim()
            Page(idx, imageUrl = imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder().apply {
            if (!page.imageUrl!!.contains("batcave.biz")) {
                removeAll("Referer")
            }
        }.build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    private var publishers: List<Pair<String, Int>> = emptyList()
    private var genres: List<Pair<String, Int>> = emptyList()
    private var filterParseFailed = false

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            Filter.Header("Doesn't work with text search"),
            SortFilter(),
            YearFilter(),
        )
        if (publishers.isNotEmpty()) {
            filters.add(PublisherFilter(publishers))
        }
        if (genres.isNotEmpty()) {
            filters.add(GenreFilter(genres))
        }
        if (filters.size < 5) {
            filters.add(
                Filter.Header(
                    if (filterParseFailed) {
                        "Unable to load more filters"
                    } else {
                        "Press 'reset' to load more filters"
                    },
                ),
            )
        }

        return FilterList(filters)
    }

    private fun parseFilters(document: Document) {
        val script = document.selectFirst("script:containsData(window.__XFILTER__)")?.data() ?: run {
            filterParseFailed = true
            return
        }

        val data = try {
            script
                .substringAfter("window.__XFILTER__ = ")
                .substringBeforeLast(";")
                .trim()
                .parseAs<XFilters>()
        } catch (e: Exception) {
            Log.e(name, "filters", e)
            filterParseFailed = true
            return
        }

        publishers = data.publishers.map { it.value to it.id }
        genres = data.genres.map { it.value to it.id }
        filterParseFailed = false
    }
}
