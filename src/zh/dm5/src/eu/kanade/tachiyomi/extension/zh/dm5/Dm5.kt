package eu.kanade.tachiyomi.extension.zh.dm5

import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Dm5 :
    HttpSource(),
    ConfigurableSource {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "动漫屋"
    override val client = network.cloudflareClient.newBuilder().addInterceptor(CommentsInterceptor).build()

    private val preferences by getPreferencesLazy()
    override val baseUrl = preferences.getString(MIRROR_PREF, MIRROR_ENTRIES[0])!!

    // Some mangas are blocked without this
    override fun headersBuilder() = super.headersBuilder().set("Accept-Language", "zh-TW")
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manhua-list-p$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(POPULAR_MANGA_SELECTOR).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2.title > a")!!.text()
        thumbnail_url = element.selectFirst("p.mh-cover")!!.attr("style")
            .substringAfter("url(").substringBefore(")")
        url = element.selectFirst("h2.title > a")!!.attr("href")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manhua-list-s2-p$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(POPULAR_MANGA_SELECTOR).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search?title=$query&language=1&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(SEARCH_MANGA_SELECTOR).map { searchMangaFromElement(it) }
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".title > a")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
            ?: element.selectFirst("p.mh-cover")!!.attr("style")
                .substringAfter("url(").substringBefore(")")
        url = element.selectFirst(".title > a")!!.attr("href")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("div.banner_detail_form p.title")!!.ownText()
            thumbnail_url = document.selectFirst("div.banner_detail_form img")!!.absUrl("src")
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
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        // May need to click button on website to read
        document.selectFirst(".warning-bar")?.let { throw Exception(it.text()) }
        val container = document.selectFirst("div#chapterlistload")
            ?: throw Exception("请到 WebView 确认；切换网络环境后可尝试扩展设置里面的\u201C（动漫屋专用）清除 Cookie\u201D")
        val titles = document.select(".detail-list-title > a.block").map { it.text().substringBefore('（') }

        val li = container.select("> ul").flatMapIndexed { i, ul ->
            ul.select("li > a").map {
                SChapter.create().apply {
                    url = it.attr("href")
                    name = it.selectFirst("p.title")?.text() ?: it.text()
                    it.selectFirst(".detail-lock, .view-lock")?.let { name = "\uD83D\uDD12 $name" }
                    scanlator = titles[i]
                    it.selectFirst("p.tip")?.let { date ->
                        date_upload = dateFormat.tryParse(date.text())
                    }
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

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("div#barChapter > img.load-src")
        val result: MutableList<Page>
        val script = document.selectFirst("script:containsData(DM5_MID)")!!.data()
        if (!script.contains("DM5_VIEWSIGN_DT")) {
            throw Exception(document.selectFirst("div.view-pay-form p.subtitle")!!.text())
        }
        val cid = script.substringAfter("var DM5_CID=").substringBefore(";")
        if (images.isNotEmpty()) {
            result = images.mapIndexed { index, it ->
                Page(index, imageUrl = it.absUrl("data-src"))
            }.toMutableList()
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
            }.toMutableList()
        }

        if (preferences.getBoolean(CHAPTER_COMMENTS_PREF, false)) {
            val pageSize = script.substringAfter("var DM5_PAGEPCOUNT = ").substringBefore(";")
            val tid = script.substringAfter("var DM5_TIEBATOPICID='").substringBefore("'")
            for (i in 1..pageSize.toInt()) {
                result.add(
                    Page(
                        result.size,
                        imageUrl = "$baseUrl/m$cid/pagerdata.ashx?pageindex=$i&pagesize=$pageSize&tid=$tid&cid=$cid&t=9",
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

    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!.toHttpUrl()
        val cid = url.queryParameter("cid")!!
        val headers = headers.newBuilder().add("Referer", "$baseUrl/m$cid").build()
        return GET(url, headers)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPreference = ListPreference(screen.context).apply {
            key = MIRROR_PREF
            title = "使用镜像网址"
            entries = MIRROR_ENTRIES
            entryValues = MIRROR_ENTRIES
            setDefaultValue(MIRROR_ENTRIES[0])
        }
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
        screen.addPreference(mirrorPreference)
        screen.addPreference(chapterCommentsPreference)
        screen.addPreference(sortChapterPreference)

        SwitchPreferenceCompat(screen.context).run {
            title = "（动漫屋专用）清除 Cookie"
            summary = "切换网络环境后可尝试清除（app 自带的清除 Cookie 无效）"
            setOnPreferenceChangeListener { _, _ ->
                val message = try {
                    val manager = CookieManager.getInstance()
                    var before = 0
                    var after = 0
                    for (mirror in MIRROR_ENTRIES) {
                        val cookies = manager.getCookie(mirror) ?: continue
                        val cookieList = cookies.split("; ")
                        before += cookieList.size
                        val url = mirror.toHttpUrl()
                        val domain = url.host
                        val topDomain = url.topPrivateDomain()
                        for (cookie in cookieList) {
                            val name = cookie.substringBefore('=')
                            manager.setCookie(mirror, "$name=; Max-Age=-1; Path=/")
                            manager.setCookie(mirror, "$name=; Max-Age=-1; Domain=$domain; Path=/")
                            manager.setCookie(mirror, "$name=; Max-Age=-1; Domain=$topDomain; Path=/")
                        }
                        val cookiesAfter = manager.getCookie(mirror) ?: continue
                        after += cookiesAfter.split("; ").size
                    }
                    "一共 $before 条 Cookie，清除了 ${before - after} 条"
                } catch (e: Exception) {
                    Log.e("Dm5", "failed to clear cookies", e)
                    "清除失败：$e"
                }
                Toast.makeText(screen.context, message, Toast.LENGTH_LONG).show()
                false
            }
            screen.addPreference(this)
        }
    }

    companion object {
        private val MIRROR_ENTRIES get() = arrayOf(
            "https://www.dm5.cn",
            "https://www.dm5.com",
        )
        private const val MIRROR_PREF = "mirror"
        private const val CHAPTER_COMMENTS_PREF = "chapterComments"
        private const val SORT_CHAPTER_PREF = "sortChapter"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

        private const val POPULAR_MANGA_SELECTOR = "ul.mh-list > li > div.mh-item"
        private const val NEXT_PAGE_SELECTOR = "div.page-pagination a:contains(>)"
        private const val SEARCH_MANGA_SELECTOR = "ul.mh-list > li, div.banner_detail_form"
    }
}
