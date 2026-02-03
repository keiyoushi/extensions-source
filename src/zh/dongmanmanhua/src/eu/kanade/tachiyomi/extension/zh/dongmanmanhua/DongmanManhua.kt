package eu.kanade.tachiyomi.extension.zh.dongmanmanhua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DongmanManhua : HttpSource() {
    override val name = "Dongman Manhua"
    override val lang get() = "zh-Hans"
    override val id get() = 4222375517460530289
    override val baseUrl = "https://www.dongmanmanhua.cn"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override val client = network.cloudflareClient

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/dailySchedule", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("div#dailyList .daily_section li a, div.daily_lst.comp li a")
            .map(::mangaFromElement)
            .distinctBy { it.url }

        return MangasPage(entries, false)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("p.subj")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/dailySchedule?sortOrder=UPDATE&webtoonCompleteType=ONGOING", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val day = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "div._list_SUNDAY"
            Calendar.MONDAY -> "div._list_MONDAY"
            Calendar.TUESDAY -> "div._list_TUESDAY"
            Calendar.WEDNESDAY -> "div._list_WEDNESDAY"
            Calendar.THURSDAY -> "div._list_THURSDAY"
            Calendar.FRIDAY -> "div._list_FRIDAY"
            Calendar.SATURDAY -> "div._list_SATURDAY"
            else -> "div"
        }

        val entries = document.select("div#dailyList > $day li > a")
            .map(::mangaFromElement)
            .distinctBy { it.url }

        return MangasPage(entries, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("keyword", query)
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select("#content > div.card_wrap.search ul:not(#filterLayer) li a")
            .map(::mangaFromElement)
        val hasNextPage = document.selectFirst("div.more_area, div.paginate a[onclick] + a") != null

        return MangasPage(entries, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val detailElement = document.selectFirst(".detail_header .info")
        val infoElement = document.selectFirst("#_asideDetail")

        return SManga.create().apply {
            title = document.selectFirst("h1.subj, h3.subj")!!.text()
            author = detailElement?.selectFirst(".author:nth-of-type(1)")?.ownText()
                ?: detailElement?.selectFirst(".author_area")?.ownText()
            artist = detailElement?.selectFirst(".author:nth-of-type(2)")?.ownText()
                ?: detailElement?.selectFirst(".author_area")?.ownText() ?: author
            genre = detailElement?.select(".genre").orEmpty().joinToString { it.text() }
            description = infoElement?.selectFirst("p.summary")?.text()
            status = with(infoElement?.selectFirst("p.day_info")?.text().orEmpty()) {
                when {
                    contains("更新") -> SManga.ONGOING
                    contains("完结") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            thumbnail_url = run {
                val picElement = document.selectFirst("#content > div.cont_box > div.detail_body")
                val discoverPic = document.selectFirst("#content > div.cont_box > div.detail_header > span.thmb")
                picElement?.attr("style")
                    ?.substringAfter("url(")
                    ?.substringBeforeLast(")")
                    ?.removeSurrounding("\"")
                    ?.removeSurrounding("'")
                    ?.takeUnless { it.isBlank() }
                    ?: discoverPic?.selectFirst("img:not([alt='Representative image'])")
                        ?.attr("src")
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        var continueParsing = true
        val chapters = mutableListOf<SChapter>()

        while (continueParsing) {
            document.select("ul#_listUl li").map { chapters.add(chapterFromElement(it)) }
            document.select("div.paginate a[onclick] + a").let { element ->
                if (element.isNotEmpty()) {
                    document = client.newCall(GET(element.attr("abs:href"), headers)).execute().asJsoup()
                } else {
                    continueParsing = false
                }
            }
        }
        return chapters
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst("span.subj span")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        date_upload = dateFormat.tryParse(element.selectFirst("span.date")?.text())
    }

    private val dateFormat = SimpleDateFormat("yyyy-M-d", Locale.ENGLISH)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("div#_imageList > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("data-url"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
