package eu.kanade.tachiyomi.extension.es.plottwistnofansub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.select.Elements
import uy.kohesive.injekt.injectLazy

class PlotTwistNoFansub : ParsedHttpSource() {

    override val name = "Plot Twist No Fansub"

    override val baseUrl = "https://www.plotfansub.com"

    override val lang = "es"

    override val supportsLatest = false

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.last-updates > div.item"

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        with(element.selectFirst("div.panel-body div.mangaThumbnail")!!) {
            val mangaUrl = selectFirst("a")!!.attr("href")
                .removeSuffix("/")
                .substringBeforeLast("/")
                .replaceFirst("/reader/", "/plot/manga/")
            setUrlWithoutDomain(mangaUrl)
            thumbnail_url = select("img").imgAttr()
            title = select("img").attr("title")
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/wp-admin/admin-ajax.php"

        val body = FormBody.Builder()
            .add("action", "td_ajax_search")
            .add("td_string", query)
            .add("limit", MAX_MANGA_RESULTS.toString())
            .build()

        return POST(url, headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<SearchResultDto>(response.body.string())
        val unescapedHtml = result.data.unescape()
        val mangas = Jsoup.parse(unescapedHtml).select(searchMangaSelector())
            .map { searchMangaFromElement(it) }
        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector(): String = "div.td-cpt-manga"

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst(".entry-title a")!!.attr("href"))
        title = element.selectFirst(".entry-title a")!!.text()
        thumbnail_url = element.select("span.entry-thumb").attr("style")
            .substringAfter("url(")
            .substringBeforeLast(")")
            .removeSurrounding("'")
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        with(document.selectFirst("div.td-ss-main-content")!!) {
            title = selectFirst("div.td-post-header .entry-title p")!!.text()
            with(selectFirst("div.td-post-content")!!) {
                thumbnail_url = selectFirst("img.entry-thumb")?.imgAttr()
                description = select("> p").joinToString("\n") { it.text() }
                genre = select("div.mangaInfo > a.tagElement").joinToString { it.text() }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("div.td-ss-main-content > article[id^=post-]")!!.id().substringAfter("-")
        val url = "$baseUrl/wp-admin/admin-ajax.php"
        val formBody = FormBody.Builder()
            .add("action", "lcap")
            .add("manga_id", mangaId)

        var page = 1
        val chapterList = mutableListOf<SChapter>()

        do {
            val body = formBody
                .add("pageNumber", page.toString())
                .build()

            val result = client.newCall(POST(url, headers, body)).execute().body.string()

            val jsonArray = json.decodeFromString<List<ChapterDto>>(result)

            if (jsonArray.isEmpty()) break

            jsonArray.forEach {
                chapterList.add(
                    SChapter.create().apply {
                        val unescapedName = Entities.unescape(it.name).replaceFirstChar { it.uppercase() }
                        this.name = "Capítulo ${it.number}: $unescapedName"
                        this.url = "/reader/${it.mangaSlug}/chapter-${it.number}"
                    },
                )
            }

            page++
        } while (jsonArray.isNotEmpty())

        return chapterList
    }

    override fun chapterListSelector(): String = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script#clarity-reader-nav-js-extra")!!.data()
        val pagesJson = CHAPTER_PAGES_REGEX.find(script)!!.groupValues[1]
        val result = json.decodeFromString<PagesPayloadDto>(pagesJson)
        val mangaSlug = "${result.cdnUrl}/${result.mangaSlug}"
        val chapterNumber = result.chapterNumber
        return result.images.mapIndexed { i, img ->
            Page(i, imageUrl = "${mangaSlug}_${img.mangaId}/ch_$chapterNumber/${img.imageName}")
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Haga click en \"Filtrar\" para ver todos los mangas."),
        )
    }

    private fun Element.imgAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun Elements.imgAttr(): String = this.first()!!.imgAttr()

    private fun String.unescape(): String {
        return UNESCAPE_REGEX.replace(this, "$1")
    }

    companion object {
        private val UNESCAPE_REGEX = """\\(.)""".toRegex()
        private val CHAPTER_PAGES_REGEX = """obj\s*=\s*(.*)\s*;""".toRegex()
        private const val MAX_MANGA_RESULTS = 1000
    }
}
