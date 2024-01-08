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
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.collections.ArrayList

class TencentComics : ParsedHttpSource() {

    override val name = "腾讯动漫"

    // its easier to parse the mobile version of the website
    override val baseUrl = "https://m.ac.qq.com"

    private val desktopUrl = "https://ac.qq.com"

    override val lang = "zh"

    override val supportsLatest = true

    override val id: Long = 6353436350537369479

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    override fun chapterListSelector(): String = "ul.chapter-wrap-list.reverse > li > a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            url = element.attr("href").trim()
            name = (if (element.isLockedChapter()) "\uD83D\uDD12 " else "") + element.text().trim()
            chapter_number = element.attr("data-seq").toFloat()
        }
    }

    private fun Element.isLockedChapter(): Boolean {
        return this.selectFirst("div.lock") != null
    }

    override fun popularMangaSelector(): String = "ul.ret-search-list.clearfix > li"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = "/comic/index/" + element.select("div > a").attr("href").substringAfter("/Comic/comicInfo/")
            title = element.select("div > a").attr("title").trim()
            thumbnail_url = element.select("div > a > img").attr("data-original")
            author = element.select("div > p.ret-works-author").text().trim()
            description = element.select("div > p.ret-works-decs").text().trim()
        }
    }

    override fun popularMangaNextPageSelector() = throw java.lang.UnsupportedOperationException("Not used.")

    override fun popularMangaRequest(page: Int): Request = GET("$desktopUrl/Comic/all/search/hot/page/$page)", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        // next page buttons do not exist
        // even if the total searches happen to be 12 the website fills the next page anyway
        return MangasPage(mangas, mangas.size == 12)
    }

    override fun latestUpdatesSelector(): String = "ul.ret-search-list.clearfix > li"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = throw java.lang.UnsupportedOperationException("Not used.")

    override fun latestUpdatesRequest(page: Int): Request = GET("$desktopUrl/Comic/all/search/time/page/$page)", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // desktop version of the site has more info
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$desktopUrl/Comic/comicInfo/" + manga.url.substringAfter("/index/"), headers)

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select("div.works-cover.ui-left > a > img").attr("src")
            title = document.select("h2.works-intro-title.ui-left > strong").text().trim()
            description = document.select("p.works-intro-short").text().trim()
            author = document.select("p.works-intro-digi > span > em").text().trim()
            status = when (document.select("label.works-intro-status").text().trim()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                "連載中" -> SManga.ONGOING
                "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // convert url to desktop since some chapters are blocked on mobile
    override fun pageListRequest(chapter: SChapter): Request = GET("$desktopUrl/ComicView/" + chapter.url.substringAfter("/chapter/"), headers)

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

    override fun pageListParse(document: Document): List<Page> {
        val pages = ArrayList<Page>()
        var html = document.html()

        // Sometimes the nonce has commands that are unrunnable, just reload and hope
        var nonce = html.substringAfterLast("window[").substringAfter("] = ").substringBefore("</script>").trim()

        while (nonce.contains("document") || nonce.contains("window")) {
            html = client.newCall(GET(desktopUrl + document.select("li.now-reading > a").attr("href"), headers)).execute().body.string()
            nonce = html.substringAfterLast("window[").substringAfter("] = ").substringBefore("</script>").trim()
        }

        val raw = html.substringAfterLast("var DATA =").substringBefore("PRELOAD_NUM").trim().replace(Regex("^\'|\',$"), "")
        val decodePrefix = "var raw = \"$raw\"; var nonce = $nonce"
        val full = QuickJs.create().use { it.evaluate(decodePrefix + jsDecodeFunction).toString() }
        val chapterData = json.parseToJsonElement(String(Base64.decode(full, Base64.DEFAULT))).jsonObject

        if (!chapterData["chapter"]!!.jsonObject["canRead"]!!.jsonPrimitive.boolean) throw Exception("[此章节为付费内容]")

        val pictures = chapterData["picture"]!!.jsonArray
        for (i in 0 until pictures.size) {
            pages.add(Page(i, "", pictures[i].jsonObject["url"]!!.jsonPrimitive.content))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")

    override fun searchMangaSelector() = "ul > li.comic-item > a"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.attr("href")
            title = element.select("div > strong").text().trim()
            thumbnail_url = element.select("div > img").attr("src")
            description = element.select("div > small.comic-desc").text().trim()
            genre = element.select("div > small.comic-tag").text().trim().replace(" ", ", ")
        }
    }

    override fun searchMangaNextPageSelector() = throw java.lang.UnsupportedOperationException("Not used.")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(ID_SEARCH_PREFIX)) {
            val id = query.removePrefix(ID_SEARCH_PREFIX)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
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
            val mangas = document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }
            MangasPage(mangas, mangas.size == 10)
            // Filter search
        } else {
            val mangas = document.select(popularMangaSelector()).map { element ->
                popularMangaFromElement(element)
            }
            // next page buttons do not exist
            // even if the total searches happen to be 12 the website fills the next page anyway
            MangasPage(mangas, mangas.size == 12)
        }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("注意：不影響按標題搜索"),
        PopularityFilter(),
        VipFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class PopularityFilter : UriPartFilter(
        "热门人气/更新时间",
        arrayOf(
            Pair("热门人气", "hot/"),
            Pair("更新时间", "time/"),
        ),
    )

    private class VipFilter : UriPartFilter(
        "属性",
        arrayOf(
            Pair("全部", ""),
            Pair("付费", "vip/2/"),
            Pair("免费", "vip/1/"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "进度",
        arrayOf(
            Pair("全部", ""),
            Pair("连载中", "finish/1/"),
            Pair("已完结", "finish/2/"),
        ),
    )

    private class GenreFilter : UriPartFilter(
        "标签",
        arrayOf(
            Pair("全部", ""),
            Pair("恋爱", "105"),
            Pair("玄幻", "101"),
            Pair("异能", "103"),
            Pair("恐怖", "110"),
            Pair("剧情", "106"),
            Pair("科幻", "108"),
            Pair("悬疑", "112"),
            Pair("奇幻", "102"),
            Pair("冒险", "104"),
            Pair("犯罪", "111"),
            Pair("动作", "109"),
            Pair("日常", "113"),
            Pair("竞技", "114"),
            Pair("武侠", "115"),
            Pair("历史", "116"),
            Pair("战争", "117"),
        ),
    )

    companion object {
        const val ID_SEARCH_PREFIX = "id:"
    }
}
