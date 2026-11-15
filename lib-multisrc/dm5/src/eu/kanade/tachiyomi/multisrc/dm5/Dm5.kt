package eu.kanade.tachiyomi.multisrc.dm5

import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

abstract class Dm5 :
    KeiSource(),
    ConfigurableSource {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(CommentsInterceptor)
        addCookie("isAdult" to "1")
    }

    private val preferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders() = apply {
        // Some mangas are blocked without this
        set("Accept-Language", "zh-TW")
        // Mobile browsers are sent to the m. site, whose pages are different
        get("User-Agent")?.let { set("User-Agent", it.toDesktopUserAgent()) }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/manhua-list-p$page/")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$baseUrl/manhua-list-s2-p$page/")

    private suspend fun getMangaList(url: String): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(POPULAR_MANGA_SELECTOR).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2.title > a")!!.text()
        thumbnail_url = element.selectFirst("p.mh-cover")?.attr("style")
            ?.substringAfter("url(")?.substringBefore(")")
        setUrlWithoutDomain(element.selectFirst("h2.title > a")!!.absUrl("href"))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("title", query)
            .addQueryParameter("language", "1")
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(SEARCH_MANGA_SELECTOR).map {
            SManga.create().apply {
                title = it.selectFirst(".title > a")!!.text()
                setUrlWithoutDomain(it.selectFirst(".title > a")!!.absUrl("href"))
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
                    ?: it.selectFirst("p.mh-cover")?.attr("style")
                        ?.substringAfter("url(")?.substringBefore(")")
            }
        }
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage)
    }

    // Series and chapter links, from any host of the site and in any of its path forms
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.siteName() != baseUrl.toHttpUrl().siteName()) return null
        var document = client.get(baseUrl.toHttpUrl().newBuilder().encodedPath(url.encodedPath).build(), Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(DETAILS_SELECTOR) == null) {
            val mangaUrl = document.selectFirst("a.back")?.absUrl("href") ?: return null
            document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        }
        return SManga.create().apply {
            setUrlWithoutDomain(document.location())
            parseDetails(document)
            initialized = true
        }
    }

    private fun HttpUrl.siteName() = topPrivateDomain()?.substringBefore('.')

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.parseDetails(document)
        return SMangaUpdate(manga, parseChapterList(document))
    }

    private fun SManga.parseDetails(document: Document) {
        val details = document.selectFirst(DETAILS_SELECTOR)!!
        title = details.selectFirst("p.title")!!.ownText()
        thumbnail_url = details.selectFirst("img")?.absUrl("src")
        author = details.selectFirst("p.subtitle > a")?.text()
        artist = author
        genre = details.select("p.tip a").eachText().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .rating(details.selectFirst("p.title span.score")?.text()?.removeSuffix("分")?.toDoubleOrNull())
            .description(
                details.selectFirst("p.content")?.let {
                    it.ownText() + it.selectFirst("span")?.ownText().orEmpty()
                },
            )
            .build()
        status = when (details.selectFirst("p.tip > span > span")?.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        // May need to click button on website to read
        document.selectFirst(".warning-bar")?.let { throw Exception(it.text()) }
        val container = document.selectFirst("div#chapterlistload")
            ?: throw Exception("请到 WebView 确认；切换网络环境后可尝试扩展设置里面的\u201C（${name}专用）清除 Cookie\u201D")
        val titles = document.select(".detail-list-title > a.block").map { it.text().substringBefore('（') }

        val li = container.select("> ul").flatMapIndexed { i, ul ->
            ul.select("li > a").map {
                SChapter.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    name = it.selectFirst("p.title")?.text() ?: it.text()
                    it.selectFirst(".detail-lock, .view-lock")?.let { name = "\uD83D\uDD12 $name" }
                    scanlator = titles.getOrNull(i)
                    it.selectFirst("p.tip")?.let { date ->
                        date_upload = dateFormat.tryParseDate(date.text(), zone)
                    }
                }
            }
        }

        // Sort chapter by id (related to upload time), the number at the end of the url
        if (preferences.getBoolean(SORT_CHAPTER_PREF, false)) {
            return li.sortedByDescending { CHAPTER_ID_REGEX.find(it.url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L }
        }

        // Sometimes list is in ascending order, probably unread paid manga
        return if (document.selectFirst("div.detail-list-title a.order")?.text() == "正序") {
            li.reversed()
        } else {
            li
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val chapterUrl = document.location()
        val images = document.select("div#barChapter > img.load-src")
        val result: MutableList<Page>
        val script = document.selectFirst("script:containsData(DM5_MID)")!!.data()
        if (!script.contains("DM5_VIEWSIGN_DT")) {
            throw Exception(document.selectFirst("div.view-pay-form p.subtitle")?.text() ?: "Required viewsign data missing from script")
        }
        val cid = script.substringAfter("var DM5_CID=").substringBefore(";")
        if (images.isNotEmpty()) {
            result = images.mapIndexed { index, it ->
                Page(index, url = chapterUrl, imageUrl = it.absUrl("data-src"))
            }.toMutableList()
        } else {
            val mid = script.substringAfter("var DM5_MID=").substringBefore(";")
            val dt = script.substringAfter("var DM5_VIEWSIGN_DT=\"").substringBefore("\";")
            val sign = script.substringAfter("var DM5_VIEWSIGN=\"").substringBefore("\";")
            val imageCount = script.substringAfter("var DM5_IMAGE_COUNT=").substringBefore(";").toInt()
            result = (1..imageCount).map {
                val url = chapterUrl.toHttpUrl().newBuilder()
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
                Page(it, url = url.toString())
            }.toMutableList()
        }

        if (preferences.getBoolean(CHAPTER_COMMENTS_PREF, false)) {
            val pageSize = script.substringAfter("var DM5_PAGEPCOUNT = ").substringBefore(";")
            val tid = script.substringAfter("var DM5_TIEBATOPICID='").substringBefore("'")
            for (i in 1..pageSize.toInt()) {
                result.add(
                    Page(
                        result.size,
                        url = chapterUrl,
                        imageUrl = "$baseUrl/m$cid/pagerdata.ashx?pageindex=$i&pagesize=$pageSize&tid=$tid&cid=$cid&t=9",
                    ),
                )
            }
        }
        return result
    }

    override suspend fun getImageUrl(page: Page): String {
        val response = client.get(page.url, Fetch.SCRIPT.from(page.url.substringBefore("chapterfun.ashx")))
        val script = Unpacker.unpack(response.body.string())
        val pix = script.substringAfter("var pix=\"").substringBefore("\"")
        val pvalue = script.substringAfter("var pvalue=[\"").substringBefore("\"")
        val query = script.substringAfter("pix+pvalue[i]+\"").substringBefore("\"")
        return pix + pvalue + query
    }

    // The chapter page's referrer policy (no-referrer-when-downgrade) sends its full address, which the image host checks:
    // Fetch.from would cut it to the origin, as the default policy does
    override fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .fetch(Fetch.IMAGE)
        .header("Referer", page.url.substringBefore("chapterfun.ashx"))
        .build()

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

        SwitchPreferenceCompat(screen.context).run {
            title = "（${name}专用）清除 Cookie"
            summary = "切换网络环境后可尝试清除（app 自带的清除 Cookie 无效）"
            setOnPreferenceChangeListener { _, _ ->
                val message = try {
                    val manager = CookieManager.getInstance()
                    var before = 0
                    var after = 0

                    val cookies = manager.getCookie(baseUrl)
                    if (cookies != null) {
                        val cookieList = cookies.split("; ")
                        before += cookieList.size
                        val url = baseUrl.toHttpUrl()
                        val domain = url.host
                        val topDomain = url.topPrivateDomain()
                        for (cookie in cookieList) {
                            val name = cookie.substringBefore('=')
                            manager.setCookie(baseUrl, "$name=; Max-Age=-1; Path=/")
                            manager.setCookie(baseUrl, "$name=; Max-Age=-1; Domain=$domain; Path=/")
                            manager.setCookie(baseUrl, "$name=; Max-Age=-1; Domain=$topDomain; Path=/")
                        }
                        val cookiesAfter = manager.getCookie(baseUrl)
                        if (cookiesAfter != null) {
                            after += cookiesAfter.split("; ").size
                        }
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
        private const val CHAPTER_COMMENTS_PREF = "chapterComments"
        private const val SORT_CHAPTER_PREF = "sortChapter"
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val zone = ZoneId.of("Asia/Shanghai")
        private val CHAPTER_ID_REGEX = Regex("""(\d+)/?$""")

        private const val POPULAR_MANGA_SELECTOR = "ul.mh-list > li > div.mh-item"
        private const val NEXT_PAGE_SELECTOR = "div.page-pagination a:contains(>)"
        private const val SEARCH_MANGA_SELECTOR = "ul.mh-list > li, div.banner_detail_form"
        private const val DETAILS_SELECTOR = "div.banner_detail_form"
    }
}

// The app's mobile Chrome user agent, as the same Chrome on a Windows desktop
private fun String.toDesktopUserAgent() = replace(ANDROID_PLATFORM_REGEX, "(Windows NT 10.0; Win64; x64)").replace(" Mobile Safari/", " Safari/")

private val ANDROID_PLATFORM_REGEX = Regex("""\(Linux; Android[^)]*\)""")
