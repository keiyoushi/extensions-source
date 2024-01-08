package eu.kanade.tachiyomi.extension.en.likemanga

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class LikeManga : ParsedHttpSource() {

    override val name = "LikeManga"

    override val lang = "en"

    override val baseUrl = "https://likemanga.io"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList(SortFilter("top-manga")))
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList(SortFilter("lastest-chap")))
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)
    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("act", "searchadvance")
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.checked?.forEach {
                            addQueryParameter("f[genres][]", it)
                        }
                    }
                    is ChapterCountFilter -> {
                        filter.selected?.let {
                            addQueryParameter("f[min_num_chapter]", it)
                        }
                    }
                    is StatusFilter -> {
                        filter.selected?.let {
                            addQueryParameter("f[status]", it)
                        }
                    }
                    is SortFilter -> {
                        filter.selected?.let {
                            addQueryParameter("f[sortby]", it)
                        }
                    }
                    else -> {}
                }
            }
            if (query.isNotEmpty()) {
                addQueryParameter("f[keyword]", query.trim())
            }
            if (page > 1) {
                addQueryParameter("pageNum", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    private var genresList: List<Pair<String, String>> = emptyList()

    private fun parseGenres(document: Document): List<Pair<String, String>> {
        return document.selectFirst("div.search_genres")
            ?.select("div.form-check")
            .orEmpty()
            .mapNotNull {
                val label = it.selectFirst("label")
                    ?.text()?.trim() ?: return@mapNotNull null

                val value = it.selectFirst("input")
                    ?.attr("value") ?: return@mapNotNull null

                Pair(label, value)
            }
    }

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
            StatusFilter(),
            ChapterCountFilter(),
        )

        filters += if (genresList.isEmpty()) {
            listOf(
                Filter.Separator(),
                Filter.Header("Press 'reset' to attempt to show Genres"),
            )
        } else {
            listOf(
                GenreFilter("Genre", genresList),
            )
        }

        return FilterList(filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (genresList.isEmpty()) {
            val document = response.peekBody(Long.MAX_VALUE).string()
                .let { Jsoup.parse(it, response.request.url.toString()) }

            genresList = parseGenres(document)
        }

        return super.searchMangaParse(response)
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        title = element.select(".title-manga").text()
    }

    override fun searchMangaSelector() = "div.card-body div.card"
    override fun searchMangaNextPageSelector() = "ul.pagination a:contains(»)"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("#title-detail-manga").text()
        thumbnail_url = document.selectFirst(".detail-info img")?.imgAttr()
        description = document.selectFirst("#summary_shortened")?.text()?.trim()
        genre = document.select(".list-info a[href*=/genres/]").joinToString { it.text() }
        status = document.selectFirst(".list-info .status p:nth-child(2)")?.text().parseStatus()
        author = document.selectFirst(".list-info .author p:nth-child(2)")?.text()
            ?.takeUnless { it.trim() == "Updating" }
    }

    private fun String?.parseStatus(): Int {
        if (this == null) return SManga.UNKNOWN

        return when {
            contains("Complete", true) -> SManga.COMPLETED
            contains("In process", true) -> SManga.ONGOING
            contains("Pause", true) -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.use { it.asJsoup() }

        val chapters = document.select(chapterListSelector())
            .map(::chapterFromElement)
            .toMutableList()

        val lastPage = document.select("div.chapters_pagination a:not(.next)").last()
            ?.attr("onclick")
            ?.substringAfter("(")
            ?.substringBefore(")")
            ?.toIntOrNull()
            ?: return chapters

        val id = document.select("#title-detail-manga").attr("data-manga")
            .toIntOrNull() ?: return chapters

        for (page in 2..lastPage) {
            chapters.addAll(fetchAjaxChapterList(id, page))
        }

        return chapters
    }

    private fun fetchAjaxChapterList(id: Int, page: Int): List<SChapter> {
        val request = ajaxChapterListRequest(id, page)
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()
            return emptyList()
        }

        return ajaxChapterListParse(response)
    }

    private fun ajaxChapterListRequest(id: Int, page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("act", "ajax")
            addQueryParameter("code", "load_list_chapter")
            addQueryParameter("manga_id", id.toString())
            addQueryParameter("page_num", page.toString())
            addQueryParameter("chap_id", "0")
            addQueryParameter("keyword", "")
        }.build()

        return GET(url, headers)
    }

    private fun ajaxChapterListParse(response: Response): List<SChapter> {
        val responseJson = response.use { json.parseToJsonElement(it.body.string()) }.jsonObject
        val htmlString = responseJson["list_chap"]!!.jsonPrimitive.content
        val document = Jsoup.parseBodyFragment(htmlString, response.request.url.toString())

        return document.select(chapterListSelector())
            .map(::chapterFromElement)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name = element.select("a").text()
        date_upload = element.selectFirst(".chapter-release-date")?.text().parseDate()
    }

    override fun chapterListSelector() = ".wp-manga-chapter"

    private fun String?.parseDate(): Long {
        return runCatching {
            dateFormat.parse(this!!)!!.time
        }.getOrDefault(0L)
    }

    override fun pageListParse(document: Document): List<Page> {
        val element = document.selectFirst("div.reading input#next_img_token")

        if (element != null) {
            val imgCdnUrl = document.selectFirst("div.reading #currentlink")?.attr("value")
                ?: throw Exception("Could not find image CDN URL")

            val token = element.attr("value").split(".")[1]
            val jsonData = json.parseToJsonElement(String(Base64.decode(token, Base64.DEFAULT))).jsonObject
            val encodedImgArray = jsonData["data"]!!.jsonPrimitive.content
            val imgArray = String(Base64.decode(encodedImgArray, Base64.DEFAULT))

            return json.parseToJsonElement(imgArray).jsonArray.mapIndexed { i, img ->
                Page(i, "", "$imgCdnUrl/${img.jsonPrimitive.content}")
            }
        }

        return document.select("div.reading-detail.box_doc img:not(noscript img)")
            .mapIndexed { i, img -> Page(i, "", img.imgAttr()) }
    }

    private fun Element.imgAttr(): String? {
        return when {
            hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not Used")

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
        }
    }
}
