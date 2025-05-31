package eu.kanade.tachiyomi.extension.zh.dm5

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Dm5 : ParsedHttpSource(), ConfigurableSource {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "动漫屋"
    override val baseUrl = "https://www.dm5.cn"
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(CommentsInterceptor)
        .build()

    private val preferences: SharedPreferences = getPreferences()

    // Some mangas are blocked without this
    override fun headersBuilder() = super.headersBuilder().set("Accept-Language", "zh-TW")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manhua-list-p$page/", headers)
    override fun popularMangaNextPageSelector(): String = "div.page-pagination a:contains(>)"
    override fun popularMangaSelector(): String = "ul.mh-list > li > div.mh-item"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2.title > a")!!.text()
        thumbnail_url = element.selectFirst("p.mh-cover")!!.attr("style")
            .substringAfter("url(").substringBefore(")")
        url = element.selectFirst("h2.title > a")!!.attr("href")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manhua-list-s2-p$page/", headers)
    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?title=$query&language=1&page=$page", headers)
    }
    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = "ul.mh-list > li, div.banner_detail_form"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".title > a")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("src")
            ?: element.selectFirst("p.mh-cover")!!.attr("style")
                .substringAfter("url(").substringBefore(")")
        url = element.selectFirst(".title > a")!!.attr("href")
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("div.banner_detail_form p.title")!!.ownText()
        thumbnail_url = document.selectFirst("div.banner_detail_form img")!!.attr("abs:src")
        author = document.selectFirst("div.banner_detail_form p.subtitle > a")!!.text()
        artist = author
        genre = document.select("div.banner_detail_form p.tip a").eachText().joinToString(", ")
        val el = document.selectFirst("div.banner_detail_form p.content")!!
        description = el.ownText() + el.selectFirst("span")?.ownText().orEmpty()
        status = when (document.selectFirst("div.banner_detail_form p.tip > span > span")!!.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        // May need to click button on website to read
        document.selectFirst("ul#detail-list-select-1")?.attr("class")
            ?: throw Exception("請到webview確認")
        val li = document.select("div#chapterlistload li > a").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = if (it.selectFirst("span.detail-lock, span.view-lock") != null) {
                    "\uD83D\uDD12"
                } else {
                    ""
                } + (it.selectFirst("p.title")?.text() ?: it.text())

                val dateStr = it.selectFirst("p.tip")
                if (dateStr != null) {
                    date_upload = dateFormat.parse(dateStr.text())?.time ?: 0L
                }
            }
        }

        // Sort chapter by url (related to upload time)
        if (preferences.getBoolean(SORT_CHAPTER_PREF, false)) {
            return li.sortedByDescending { it.url.drop(2).dropLast(1).toInt() }
        }

        // Sometimes list is in ascending order, probably unread paid manga
        return if (document.selectFirst("div.detail-list-title a.order")!!.text() == "正序") {
            li.reversed()
        } else {
            li
        }
    }
    override fun chapterListSelector(): String = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("div#barChapter > img.load-src")
        val result: ArrayList<Page>
        val script = document.selectFirst("script:containsData(DM5_MID)")!!.data()
        if (!script.contains("DM5_VIEWSIGN_DT")) {
            throw Exception(document.selectFirst("div.view-pay-form p.subtitle")!!.text())
        }
        val cid = script.substringAfter("var DM5_CID=").substringBefore(";")
        if (!images.isEmpty()) {
            result = images.mapIndexed { index, it ->
                Page(index, "", it.attr("data-src"))
            } as ArrayList<Page>
        } else {
            val mid = script.substringAfter("var DM5_MID=").substringBefore(";")
            val dt = script.substringAfter("var DM5_VIEWSIGN_DT=\"").substringBefore("\";")
            val sign = script.substringAfter("var DM5_VIEWSIGN=\"").substringBefore("\";")
            val requestUrl = document.location()
            val imageCount = script.substringAfter("var DM5_IMAGE_COUNT=").substringBefore(";").toInt()
            result = (1..imageCount).map {
                val url = requestUrl.toHttpUrl().newBuilder()
                    .addPathSegment("chapterfun.ashx")
                    .addQueryParameter("cid", cid)
                    .addQueryParameter("page", it.toString())
                    .addQueryParameter("key", "")
                    .addQueryParameter("language", "1")
                    .addQueryParameter("gtk", "6")
                    .addQueryParameter("_cid", cid)
                    .addQueryParameter("_mid", mid)
                    .addQueryParameter("_dt", dt)
                    .addQueryParameter("_sign", sign)
                    .build()
                Page(it, url.toString())
            } as ArrayList<Page>
        }

        if (preferences.getBoolean(CHAPTER_COMMENTS_PREF, false)) {
            val pageSize = script.substringAfter("var DM5_PAGEPCOUNT = ").substringBefore(";")
            val tid = script.substringAfter("var DM5_TIEBATOPICID='").substringBefore("'")
            for (i in 1..pageSize.toInt()) {
                result.add(
                    Page(
                        result.size,
                        "",
                        "$baseUrl/m$cid/pagerdata.ashx?pageindex=$i&pagesize=$pageSize&tid=$tid&cid=$cid&t=9",
                    ),
                )
            }
        }
        return result
    }

    override fun imageUrlRequest(page: Page): Request {
        val referer = page.url.substringBefore("chapterfun.ashx")
        val header = headers.newBuilder().add("Referer", referer).build()
        return GET(page.url, header)
    }

    override fun imageUrlParse(response: Response): String {
        val script = Unpacker.unpack(response.body.string())
        val pix = script.substringAfter("var pix=\"").substringBefore("\"")
        val pvalue = script.substringAfter("var pvalue=[\"").substringBefore("\"")
        val query = script.substringAfter("pix+pvalue[i]+\"").substringBefore("\"")
        return pix + pvalue + query
    }
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val header = headers.newBuilder().add("Referer", baseUrl).build()
        return GET(page.imageUrl!!, header)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val chapterCommentsPreference = SwitchPreferenceCompat(screen.context).apply {
            key = CHAPTER_COMMENTS_PREF
            title = "章末吐槽页"
            summary = "修改后，已加载的章节需要清除章节缓存才能生效。"
            setDefaultValue(false)
        }
        val sortChapterPreference = SwitchPreferenceCompat(screen.context).apply {
            key = SORT_CHAPTER_PREF
            title = "依照上傳時間排序章節"
            setDefaultValue(false)
        }
        screen.addPreference(chapterCommentsPreference)
        screen.addPreference(sortChapterPreference)
    }

    companion object {
        private const val CHAPTER_COMMENTS_PREF = "chapterComments"
        private const val SORT_CHAPTER_PREF = "sortChapter"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
