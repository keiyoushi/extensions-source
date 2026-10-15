package eu.kanade.tachiyomi.extension.zh.tencentcomics

import android.util.Base64
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class TencentComics : KeiSource() {

    // its easier to parse the mobile version of the website

    private val desktopUrl = "https://ac.qq.com"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")

    override suspend fun getPopularManga(page: Int): MangasPage = parsePopularMangaPage(client.get("$desktopUrl/Comic/all/search/hot/page/$page").asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parsePopularMangaPage(client.get("$desktopUrl/Comic/all/search/time/page/$page").asJsoup())

    // desktop version of the site has more info
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$desktopUrl/Comic/comicInfo/" + manga.url.substringAfter("/index/")).asJsoup()
        val details = mangaDetailsParse(document).apply { url = manga.url }
        val chapterList = document.select(".chapter-page-all .works-chapter-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.select("a").attr("abs:href"))
                name = (if (element.isLockedChapter()) "\uD83D\uDD12 " else "") + element.text()
            }
        }.reversed()
        return SMangaUpdate(details, chapterList)
    }

    private fun Element.isLockedChapter(): Boolean = selectFirst(".ui-icon-pay") != null

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("div.works-cover.ui-left > a > img").attr("src")
        title = document.select(".works-intro-title > strong").text()
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

    private val dataRegex = Regex("^'|',\$")

    private val browserStubs = """
        var window = this;
        var document = new Proxy(function () {}, {
            get: function () { return document; },
            apply: function () { return document; },
        });
    """

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

    // convert url to desktop since some chapters are blocked on mobile
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val html = client.get(desktopUrl + chapter.url).use { it.body.string() }

        // The nonce is a JS expression that probes browser globals, e.g. eval("!!document.children")
        val nonce = html.substringAfterLast("window[").substringAfter("] = ").substringBefore("</script>").trim()

        val raw = html.substringAfterLast("var DATA =").substringBefore("PRELOAD_NUM").trim().replace(dataRegex, "")
        val decodePrefix = "$browserStubs var raw = \"$raw\"; var nonce = $nonce"
        val full = QuickJs.create().use { it.evaluate(decodePrefix + jsDecodeFunction).toString() }
        val chapterData = String(Base64.decode(full, Base64.DEFAULT)).parseAs<ChapterData>()

        return chapterData.toPageList()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments[3]
        val document = client.get("$baseUrl/comic/index/id/$id").asJsoup()
        return mangaDetailsParse(document).apply { this.url = "/comic/index/id/$id" }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // impossible to search a manga use the filters
        val response = if (query.isNotEmpty()) {
            // mobile search redirects to the JS-only desktop search when a desktop UA is sent
            client.get("$baseUrl/search/result?word=$query&page=$page", headersBuilder().set("User-Agent", MOBILE_UA).build())
        } else {
            var genre = filters.firstInstance<GenreFilter>().toUriPart()
            if (genre.isNotEmpty()) genre = "theme/$genre/"
            val status = filters.firstInstance<StatusFilter>().toUriPart()
            val popularity = filters.firstInstance<PopularityFilter>().toUriPart()
            val vip = filters.firstInstance<VipFilter>().toUriPart()
            client.get("$desktopUrl/Comic/all/$genre${status}search/$popularity${vip}page/$page")
        }
        val isMobile = response.request.url.host.contains("m.ac.qq.com")
        val document = response.asJsoup()
        // Normal search
        return if (isMobile) {
            val mangas = document.select("ul > li.comic-item > a").map { parseSearchMangaElement(it) }
            MangasPage(mangas, mangas.size == 10)
            // Filter search
        } else {
            parsePopularMangaPage(document)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
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
        private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
