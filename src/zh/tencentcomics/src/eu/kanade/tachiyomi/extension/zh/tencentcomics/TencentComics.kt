package eu.kanade.tachiyomi.extension.zh.tencentcomics

import android.util.Base64
import app.cash.quickjs.QuickJs
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
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class TencentComics : HttpSource() {

    override val name = "腾讯动漫"

    // its easier to parse the mobile version of the website
    override val baseUrl = "https://m.ac.qq.com"

    private val desktopUrl = "https://ac.qq.com"

    override val lang = "zh-Hans"

    override val supportsLatest = true

    override val id: Long = 6353436350537369479

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")

    override fun chapterListRequest(manga: SManga): Request = GET("$desktopUrl/Comic/comicInfo/" + manga.url.substringAfter("/index/"), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".works-chapter-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.select("a").attr("abs:href"))
                name = (if (element.isLockedChapter()) "\uD83D\uDD12 " else "") + element.text()
            }
        }.reversed()
    }

    private fun Element.isLockedChapter(): Boolean = selectFirst(".ui-icon-pay") != null

    override fun popularMangaRequest(page: Int): Request = GET("$desktopUrl/Comic/all/search/hot/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parsePopularMangaPage(response.asJsoup())

    override fun latestUpdatesRequest(page: Int): Request = GET("$desktopUrl/Comic/all/search/time/page/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // desktop version of the site has more info
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$desktopUrl/Comic/comicInfo/" + manga.url.substringAfter("/index/"), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            thumbnail_url = document.select("div.works-cover.ui-left > a > img").attr("src")
            title = document.select("h2.works-intro-title.ui-left > strong").text()
            description = document.select("p.works-intro-short").text()
            author = document.select("p.works-intro-digi > span > em").text()
            status = when (document.select("label.works-intro-status").text()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                "連載中" -> SManga.ONGOING
                "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // convert url to desktop since some chapters are blocked on mobile
    override fun pageListRequest(chapter: SChapter): Request = GET(desktopUrl + chapter.url, headers)

    private val dataRegex = Regex("^'|',\$")

    private val jsDecodeFunction = """
        raw = raw.split('');
        nonce = nonce.match(/\d+[a-zA-Z]+/g);
        var len = nonce.length;
        while (len--) {
            var offset = parseInt(nonce[len]) & 255;
            var noise = nonce[len].replace(/\d+/g, '');
            raw.splice(offset, noise.length);
        }
        raw.join('');
    """

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        var html = document.html()

        // Sometimes the nonce has commands that are unrunnable, just reload and hope
        var nonce = html.substringAfterLast("window[").substringAfter("] = ").substringBefore("</script>").trim()

        while (nonce.contains("document") || nonce.contains("window")) {
            html = client.newCall(GET(desktopUrl + document.select("li.now-reading > a").attr("href"), headers)).execute().use { it.body.string() }
            nonce = html.substringAfterLast("window[").substringAfter("] = ").substringBefore("</script>").trim()
        }

        val raw = html.substringAfterLast("var DATA =").substringBefore("PRELOAD_NUM").trim().replace(dataRegex, "")
        val decodePrefix = "var raw = \"$raw\"; var nonce = $nonce"
        val full = QuickJs.create().use { it.evaluate(decodePrefix + jsDecodeFunction).toString() }
        val chapterData = String(Base64.decode(full, Base64.DEFAULT)).parseAs<ChapterData>()

        return chapterData.toPageList()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(ID_SEARCH_PREFIX)) {
        val id = query.removePrefix(ID_SEARCH_PREFIX)
        client.newCall(searchMangaByIdRequest(id))
            .asObservableSuccess()
            .map { response -> searchMangaByIdParse(response, id) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/comic/index/id/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val sManga = mangaDetailsParse(response)
        sManga.url = "/comic/index/id/$id"
        return MangasPage(listOf(sManga), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // impossible to search a manga use the filters
        return if (query.isNotEmpty()) {
            GET("$baseUrl/search/result?word=$query&page=$page", headers)
        } else {
            lateinit var genre: String
            lateinit var status: String
            lateinit var popularity: String
            lateinit var vip: String
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        genre = filter.toUriPart()
                        if (genre.isNotEmpty()) genre = "theme/$genre/"
                    }

                    is StatusFilter -> {
                        status = filter.toUriPart()
                    }

                    is PopularityFilter -> {
                        popularity = filter.toUriPart()
                    }

                    is VipFilter -> {
                        vip = filter.toUriPart()
                    }

                    else -> {}
                }
            }
            GET("$desktopUrl/Comic/all/$genre${status}search/$popularity${vip}page/$page")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        // Normal search
        return if (response.request.url.host.contains("m.ac.qq.com")) {
            val mangas = document.select("ul > li.comic-item > a").map { parseSearchMangaElement(it) }
            MangasPage(mangas, mangas.size == 10)
            // Filter search
        } else {
            parsePopularMangaPage(document)
        }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("注意：不影響按標題搜索"),
        PopularityFilter(),
        VipFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private fun parsePopularMangaPage(document: Document): MangasPage {
        val mangas = document.select("ul.ret-search-list.clearfix > li").map { parsePopularMangaElement(it) }
        // next page buttons do not exist
        // even if the total searches happen to be 12 the website fills the next page anyway
        return MangasPage(mangas, mangas.size == 12)
    }

    private fun parsePopularMangaElement(element: Element): SManga = SManga.create().apply {
        url = "/comic/index/" + element.select("div > a").attr("href").substringAfter("/Comic/comicInfo/")
        title = element.select("div > a").attr("title").trim()
        thumbnail_url = element.select("div > a > img").attr("data-original")
        author = element.select("div > p.ret-works-author").text()
        description = element.select("div > p.ret-works-decs").text()
    }

    private fun parseSearchMangaElement(element: Element): SManga = SManga.create().apply {
        url = element.attr("href")
        title = element.select("div > strong").text()
        thumbnail_url = element.select("div > img").attr("src")
        description = element.select("div > small.comic-desc").text()
        genre = element.select("div > small.comic-tag").text().replace(" ", ", ")
    }

    companion object {
        const val ID_SEARCH_PREFIX = "id:"
    }
}
