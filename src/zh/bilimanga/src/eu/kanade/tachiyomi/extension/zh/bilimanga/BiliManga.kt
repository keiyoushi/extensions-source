package eu.kanade.tachiyomi.extension.zh.bilimanga

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class BiliManga : HttpSource(), ConfigurableSource {

    override val baseUrl = "https://www.bilimanga.net"
    override val lang = "zh"
    override val name = "嗶哩漫畫"
    override val supportsLatest = true
    override val client = super.client.newBuilder().addNetworkInterceptor(MangaInterceptor()).build()

    private val preferences by getPreferencesLazy()
    private val SManga.id get() = MANGA_ID_REGEX.find(url)!!.groups[1]!!.value
    private fun String.convert(): String {
        return this.map { if (it in '０'..'９') it - 65248 else it }.joinToString("")
    }

    companion object {
        const val PAGE_SIZE = 50
        val META_REGEX = Regex("連載|完結|收藏|推薦|热度")
        val DATE_REGEX = Regex("\\d{4}-\\d{1,2}-\\d{1,2}")
        val MANGA_ID_REGEX = Regex("/detail/(\\d+)\\.html")
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "zh")
        .add("Accept", "*/*")
        .add("Cookie", "night=0")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context).forEach(screen::addPreference)
    }

    private fun getChapterUrlByContext(i: Int, els: Elements) = if (i == 0) {
        "${els[1].attr("href")}#prev"
    } else {
        "${els[i - 1].attr("href")}#next"
    }

    // Popular Page

    override fun popularMangaRequest(page: Int): Request {
        val suffix = preferences.getString(POPULAR_MANGA_DISPLAY, "/top/weekvisit/%d.html")!!
        return GET(baseUrl + String.format(suffix, page), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".book-layout").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                it.selectFirst("img")!!.let {
                    thumbnail_url = it.absUrl("data-src")
                    title = it.attr("alt")
                }
            }
        }
        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/top/lastupdate/$page.html", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search Page

    override fun getFilterList() = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegment("search").addPathSegment("${query}_$page.html")
        } else {
            url.addPathSegment("top").addPathSegment(filters[1].toString())
                .addPathSegment("$page.html")
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("detail")) {
            return MangasPage(listOf(mangaDetailsParse(response)), false)
        }
        return popularMangaParse(response)
    }

    // Manga Detail Page

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val doc = response.asJsoup()
        val meta = doc.selectFirst(".book-meta")!!.text().split("|")
        val extra = meta.filterNot(META_REGEX::containsMatchIn)
        val isNative = preferences.getBoolean(NATIVE_TITLE, false)
        url = doc.location()
        title = doc.selectFirst(if (isNative) ".backupname" else ".book-title")!!.text()
        thumbnail_url = doc.selectFirst(".book-cover")!!.attr("src")
        description = doc.selectFirst("#bookSummary")?.text()
        artist = doc.selectFirst(".authorname")?.text()
        author = doc.selectFirst(".illname")?.text() ?: artist
        status = when (meta.firstOrNull()) {
            "連載" -> SManga.ONGOING
            "完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = (doc.select(".tag-small").map(Element::text) + extra).joinToString()
        initialized = true
    }

    // Catalog Page

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/read/${manga.id}/catalog", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val info = document.selectFirst(".chapter-sub-title")!!.text()
        val date = DATE_FORMAT.tryParse(DATE_REGEX.find(info)?.value)
        val elements = document.select(".chapter-li-a")
        return elements.mapIndexed { i, e ->
            val url = e.absUrl("href").takeUnless("javascript:cid(1)"::equals)
            SChapter.create().apply {
                name = e.text().convert()
                date_upload = date
                if (url == null) scanlator = "該章节鏈接由插件預測生成"
                setUrlWithoutDomain(url ?: getChapterUrlByContext(i, elements))
            }
        }.reversed()
    }

    // Manga View Page

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val images = doc.select(".imagecontent")
        require(images.size > 0) { doc.selectFirst("#acontentz")?.text() ?: "章节鏈接错误" }
        return images.mapIndexed { i, it ->
            Page(i, doc.location(), it.attr("data-src"))
        }
    }

    // Image

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
