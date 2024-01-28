package eu.kanade.tachiyomi.extension.vi.truyengihot

import android.util.Log
import eu.kanade.tachiyomi.extension.vi.truyengihot.TruyenGiHotUtils.imgAttr
import eu.kanade.tachiyomi.extension.vi.truyengihot.TruyenGiHotUtils.textWithNewlines
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy

class TruyenGiHot : ParsedHttpSource() {

    override val name: String = "TruyenGiHot"

    override val baseUrl: String = "https://truyengihotqua.com"

    override val lang: String = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(
            page,
            "",
            FilterList(
                SortFilter(
                    getSortItems(),
                    Filter.Sort.Selection(2, false),
                ),
                CategoryFilter(0),
            ),
        )

    override fun popularMangaSelector(): String = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(
            page,
            "",
            FilterList(
                SortFilter(
                    getSortItems(),
                    Filter.Sort.Selection(0, false),
                ),
                CategoryFilter(0),
            ),
        )

    override fun latestUpdatesSelector(): String = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = searchMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                var id = query.removePrefix(PREFIX_ID_SEARCH).trim()
                if (!id.endsWith(".html")) {
                    id += ".html"
                }
                if (!id.startsWith("/")) {
                    id = "/$id"
                }

                fetchMangaDetails(
                    SManga.create().apply {
                        url = id
                    },
                )
                    .map { MangasPage(listOf(it.apply { url = id }), false) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        runCatching { fetchFilterOptions() }

        val url =
            "$baseUrl/danh-sach-truyen.html?listType=thumb&page=$page".toHttpUrl().newBuilder()
                .apply {
                    addQueryParameter("text_add", query)

                    (if (filters.isEmpty()) getFilterList() else filters)
                        .filterIsInstance<UriFilter>()
                        .forEach { it.addToUri(this) }
                }.build()
        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = "ul.contentList li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val anchor = element.select("span.title a")
        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text()
        thumbnail_url = element.selectFirst("span.thumb img")?.imgAttr()
    }

    override fun searchMangaNextPageSelector(): String = "li.page-next"

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select(".cover-title").text()
        author = document.select("p.cover-artist:contains(Tác giả) a").joinToString { it.text() }
        genre = document.select("a.manga-tags").joinToString { it.text().removePrefix("#") }
        thumbnail_url = document.selectFirst("div.cover-image img")?.imgAttr()

        val tags = document.select("img.top-tags.top-tags-full").map {
            it.attr("src").substringAfterLast("/").substringBefore(".png")
        }
        status = when {
            tags.contains("ongoing") -> SManga.ONGOING
            tags.contains("drop") -> SManga.CANCELLED
            tags.contains("full") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        description = document.select("div.content div.textArea").run {
            select("p").first()?.prepend("|truyengihay-split|")
            textWithNewlines().substringAfter("|truyengihay-split|").substringBefore(" Xem thêm")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val contentType = document.select("ul.breadcrumb li")[1].text()

        // Because they show up even with a manga filter in place
        if (contentType == "Novel" || contentType == "Anime") {
            return emptyList()
        }

        return document.select(chapterListSelector()).map {
            chapterFromElement(it)
        }
    }

    override fun chapterListSelector(): String = "ul#episode_list li a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))

        val infoBlock = element.selectFirst("span.info")!!
        name = infoBlock.select("span.no").text()
        date_upload = TruyenGiHotUtils.parseChapterDate(infoBlock.select("span.date").text())
    }

    override fun pageListParse(document: Document): List<Page> {
        val tokenScript = document.selectFirst("script:containsData(_token)")?.data()
            ?: throw Exception("Không tìm được token lấy ảnh của chapter")
        val token = tokenScript
            .substringAfter("_token = \"")
            .substringBefore("\";")

        val chapterInfoScript = document.selectFirst("script:containsData(mangaSLUG)")?.data()
            ?: throw Exception("Không tìm thấy thông tin của chapter")
        val chapterInfo = chapterInfoScript.split(";", "\n").associate {
            if (!it.contains("=")) {
                return@associate Pair("", "")
            }
            val kv = it.trim().split("=")
            val key = kv[0].removePrefix("var ").trim()
            val value = kv[1].trim().removeSurrounding("\"")
            Pair(key, value)
        }

        val formBody = FormBody.Builder()
            .add("token", token)
            .add("chapter_id", chapterInfo["c_id"]!!)
            .add("m_slug", chapterInfo["mangaSLUG"]!!)
            .add("m_id", chapterInfo["mangaID"]!!)
            .add("chapter", chapterInfo["chapter"]!!)
            .add("g_id", chapterInfo["g_id"]!!)
            .build()
        val request = POST("$baseUrl/frontend_controllers/chapter/content.php", headers, formBody)
        val response = client.newCall(request).execute().body.use {
            it.string()
        }

        val pageHtml = json.parseToJsonElement(response).jsonObject["content"]!!.jsonPrimitive.content
        val pages = Jsoup.parseBodyFragment(pageHtml, baseUrl)

        if (pages.getElementById("getImage_form") != null) {
            throw Exception("Truyện đã bị khoá!")
        }

        return Jsoup.parseBodyFragment(pageHtml, baseUrl).select("img:not([src$=wattermark.png])").mapIndexed { idx, it ->
            Page(idx, imageUrl = it.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            CategoryFilter(),
            PublicationTypeFilter(),
            FormatTypeFilter(),
            MagazineFilter(),
            ExplicitFilter(),
            StatusFilter(),
        ).also {
            if ((tags.isEmpty() && themes.isEmpty() && scanlators.isEmpty()) || fetchFiltersFailed) {
                it.add(0, Filter.Header("Nhấn 'Đặt lại' để hiện các bộ lọc"))
                it.add(1, Filter.Separator())
            }

            if (scanlators.isNotEmpty()) {
                it.add(ScanlatorFilter(scanlators.toTypedArray()))
            }

            if (tags.isNotEmpty()) {
                it.add(TagFilter(tags))
            }

            if (themes.isNotEmpty()) {
                it.add(ThemesFilter(themes))
            }

            it.add(SortFilter(getSortItems()))
        }

        return FilterList(filters)
    }

    private var tags: List<Genre> = emptyList()

    private var themes: List<Genre> = emptyList()

    private var scanlators: List<Pair<String, String>> = emptyList()

    private var fetchFiltersFailed = false

    private var fetchFiltersAttempts = 0

    private fun fetchFilterOptions() {
        if (fetchFiltersAttempts > 3 || (fetchFiltersAttempts > 0 && !fetchFiltersFailed)) {
            return
        }

        Single.fromCallable {
            val document = client.newCall(GET("$baseUrl/danh-sach-truyen.html", headers)).execute().asJsoup()

            val result = runCatching {
                tags = TruyenGiHotUtils.parseThemes(document.selectFirst("#contentTag")!!)
                themes = TruyenGiHotUtils.parseThemes(document.selectFirst("#contentTheme")!!)
                scanlators = TruyenGiHotUtils.parseOptions(document.selectFirst("#contentGroup")!!)
            }
                .onFailure {
                    Log.e("TruyenGiHot", "Could not fetch filtering options", it)
                }

            fetchFiltersFailed = result.isFailure
            fetchFiltersAttempts++
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe()
    }
}
