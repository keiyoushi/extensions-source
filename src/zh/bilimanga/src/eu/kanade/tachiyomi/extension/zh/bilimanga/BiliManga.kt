package eu.kanade.tachiyomi.extension.zh.bilimanga

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale

class BiliManga :
    HttpSource(),
    ConfigurableSource {

    override val baseUrl = "https://www.bilimanga.net"

    override val lang = "zh"

    override val name = "Bilimanga.net"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context, preferences).forEach(screen::addPreference)
    }

    override val client = super.client.newBuilder().also {
        val split = preferences.getString(PREF_RATE_LIMIT, "10/10")!!.split("/")
        it.rateLimit(split[0].toInt(), split[1].toLong())
    }.addNetworkInterceptor(MangaInterceptor()).build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/").add("Accept-Language", "zh").add("Accept", "*/*")

    // Customize

    private val SManga.id get() = MANGA_ID_REGEX.find(url)!!.groups[1]!!.value
    private fun String.toHalfWidthDigits(): String = this.map { if (it in '０'..'９') it - 65248 else it }.joinToString("")

    companion object {
        val META_REGEX = Regex("連載|完結|收藏|推薦|热度")
        val DATE_REGEX = Regex("\\d{4}-\\d{1,2}-\\d{1,2}")
        val PAGE_REGEX = Regex("第(\\d+)/(\\d+)页")
        val MANGA_ID_REGEX = Regex("/detail/(\\d+)\\.html")
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
    }

    private fun hasNextPage(doc: Document, size: Int): Boolean {
        val url = doc.location()
        return when {
            url.contains("filter") -> {
                val total = doc.selectFirst("#pagelink > .last")!!.text().toInt()
                val cur = doc.selectFirst("#pagelink > strong")!!.text().toInt()
                cur < total
            }

            url.contains("search") -> {
                val find = PAGE_REGEX.find(doc.selectFirst("#pagelink > span")!!.text())!!
                find.groups[1]!!.value.toInt() < find.groups[1]!!.value.toInt()
            }

            else -> size == 50
        }
    }

    private fun getChapterUrlByContext(i: Int, els: Elements) = when (i) {
        0 -> "${els[1].attr("href")}#prev"
        else -> "${els[i - 1].attr("href")}#next"
    }

    // Popular Page

    override fun popularMangaRequest(page: Int): Request {
        val suffix = preferences.getString(PREF_POPULAR_MANGA_DISPLAY, "/top/weekvisit/%d.html")!!
        return GET(baseUrl + String.format(suffix, page), headers)
    }

    override fun popularMangaParse(response: Response) = response.asJsoup().let { doc ->
        val mangas = doc.select(".book-layout").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                val img = it.selectFirst("img")!!
                thumbnail_url = img.absUrl("data-src")
                title = img.attr("alt")
            }
        }
        MangasPage(mangas, hasNextPage(doc, mangas.size))
    }

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/top/lastupdate/$page.html", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search Page

    override fun getFilterList() = buildFilterList()

    // https://www.bilimanga.net/filter/lastupdate_1_0_0_0_0_0_0_1_0.html
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegment("search").addPathSegment("${query}_$page.html")
        } else {
            url.addPathSegment("filter")
                .addPathSegment("${filters[4]}_${filters[1]}_${filters[7]}_${filters[5]}_${filters[3]}_${filters[2]}_${filters[8]}_${filters[6]}_${page}_0.html")
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
        val bkname = doc.selectFirst(".backupname")?.let { "**別名**：${it.text()}\n\n---\n\n" } ?: ""
        setUrlWithoutDomain(doc.location())
        title = doc.selectFirst(".book-title")!!.text()
        thumbnail_url = doc.selectFirst(".book-cover")!!.attr("src")
        description = bkname + doc.selectFirst("#bookSummary > content")?.wholeText()?.trim()
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

    override fun chapterListParse(response: Response) = response.asJsoup().let {
        val info = it.selectFirst(".chapter-sub-title")!!.text()
        val date = DATE_FORMAT.tryParse(DATE_REGEX.find(info)?.value)
        it.select(".catalog-volume").flatMap { v ->
            val chapterBar = v.selectFirst(".chapter-bar")!!.text().toHalfWidthDigits()
            val chapters = v.select(".chapter-li-a")
            chapters.mapIndexed { i, e ->
                val url = e.absUrl("href").takeUnless("javascript:cid(1)"::equals)
                SChapter.create().apply {
                    name = e.text().toHalfWidthDigits()
                    date_upload = date
                    scanlator = chapterBar
                    setUrlWithoutDomain(url ?: getChapterUrlByContext(i, chapters))
                }
            }
        }.reversed()
    }

    // Manga View Page

    override fun pageListParse(response: Response) = response.asJsoup().let {
        val images = it.select(".imagecontent")
        check(images.isNotEmpty()) {
            it.selectFirst("#acontentz")?.let { e ->
                if ("電腦端" in e.text()) "不支持電腦端查看，請在高級設置中更換移動端UA標識" else "漫畫可能已下架或需要足夠的權限"
            } ?: "章节鏈接错误"
        }
        images.mapIndexed { i, image ->
            Page(i, imageUrl = image.attr("data-src"))
        }
    }

    // Image

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
