package eu.kanade.tachiyomi.extension.zh.manhuagui

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.lzstring.LZString
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class Manhuagui(
    override val name: String = "漫画柜",
    override val lang: String = "zh",
) : HttpSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val baseHost = if (preferences.getBoolean(USE_MIRROR_URL_PREF, false)) {
        "mhgui.com"
    } else {
        "manhuagui.com"
    }

    override val baseUrl =
        if (preferences.getBoolean(SHOW_ZH_HANT_WEBSITE_PREF, false)) {
            "https://tw.$baseHost"
        } else {
            "https://www.$baseHost"
        }

    override val supportsLatest = true

    private val imageServer = arrayOf("https://i.hamreus.com", "https://cf.hamreus.com")
    private val mobileWebsiteUrl = "https://m.$baseHost"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    override val client: OkHttpClient

    init {
        val baseHttpUrl: HttpUrl = baseUrl.toHttpUrl()
        client =
            network.cloudflareClient.newBuilder()
                .rateLimitHost(baseHttpUrl, preferences.getString(MAINSITE_RATELIMIT_PREF, MAINSITE_RATELIMIT_DEFAULT_VALUE)!!.toInt(), 10)
                .rateLimitHost(imageServer[0].toHttpUrl(), preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_DEFAULT_VALUE)!!.toInt())
                .rateLimitHost(imageServer[1].toHttpUrl(), preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_DEFAULT_VALUE)!!.toInt())
                .apply {
                    if (getShowR18()) {
                        addNetworkInterceptor(AddCookieHeaderInterceptor(baseHttpUrl.host))
                    }
                }
                .build()
    }

    class AddCookieHeaderInterceptor(private val baseHost: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            if (chain.request().url.host == baseHost) {
                val originalCookies = chain.request().header("Cookie") ?: ""
                if (originalCookies.isNotEmpty() && !originalCookies.contains("isAdult=1")) {
                    return chain.proceed(
                        chain.request().newBuilder()
                            .header("Cookie", "$originalCookies; isAdult=1")
                            .build(),
                    )
                }
            }
            return chain.proceed(chain.request())
        }
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/list/view_p$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul#contList > li").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("span.current + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list/update_p$page.html", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return GET("$baseUrl/s/${query}_p$page.html", headers)
        } else {
            val params = filters.filterIsInstance<UriPartFilter>()
                .filter { it !is SortFilter }
                .map { it.toUriPart() }
                .filter { it.isNotEmpty() }
                .joinToString("_")

            val sortOrder = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: ""

            val url = when {
                sortOrder.isEmpty() -> "$baseUrl/list${params.toPathOrEmpty()}/index_p$page.html"
                sortOrder.startsWith(RANK_PREFIX) -> {
                    "$baseUrl/rank${params.toPathOrEmpty()}".let {
                        if (it.endsWith("rank")) {
                            "$it/${sortOrder.removePrefix(RANK_PREFIX).toPathOrEmpty("", ".html")}"
                        } else {
                            "$it${sortOrder.removePrefix(RANK_PREFIX).toPathOrEmpty("_")}.html"
                        }
                    }
                }
                else -> "$baseUrl/list${params.toPathOrEmpty()}/${sortOrder}_p$page.html"
            }
            return GET(url, headers)
        }
    }

    private fun String.toPathOrEmpty(prefix: String = "/", suffix: String = ""): String = if (isEmpty()) {
        this
    } else {
        "$prefix$this$suffix"
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return when {
            response.request.url.encodedPath.startsWith("/s/") -> {
                val mangas = document.select("div.book-result > ul > li").map(::searchMangaFromElement)
                val hasNextPage = document.selectFirst("span.current + a") != null
                MangasPage(mangas, hasNextPage)
            }
            response.request.url.encodedPath.startsWith("/rank/") -> {
                val mangas = document.select("td.rank-title").map {
                    SManga.create().apply {
                        url = it.selectFirst("a")?.attr("href") ?: ""
                        title = it.selectFirst("a")?.text() ?: ""
                    }
                }
                MangasPage(mangas, false)
            }
            else -> {
                val mangas = document.select("ul#contList > li").map(::mangaFromElement)
                val hasNextPage = document.selectFirst("span.current + a") != null
                MangasPage(mangas, hasNextPage)
            }
        }
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("a.bcover")?.let {
            url = it.attr("href")
            title = it.attr("title")
            val thumbnailElement = it.selectFirst("img")
            if (thumbnailElement != null) {
                thumbnail_url = if (thumbnailElement.hasAttr("src")) {
                    thumbnailElement.absUrl("src")
                } else {
                    thumbnailElement.absUrl("data-src")
                }
            }
        }
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("div.book-detail")?.let {
            val a = it.selectFirst("dl > dt > a")
            if (a != null) {
                url = a.attr("href")
                title = a.attr("title")
            }
        }
        thumbnail_url = element.selectFirst("div.book-cover > a.bcover > img")?.absUrl("src")
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(mobileWebsiteUrl + manga.url)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val call = client.newCall(GET(baseUrl + manga.url, headers))
        val bid = Regex("""\d+""").find(manga.url)?.value
        if (bid != null) {
            GlobalScope.launch(Dispatchers.IO) {
                delay(1000L)
                val callback = object : Callback {
                    override fun onFailure(call: Call, e: IOException) = e.printStackTrace()
                    override fun onResponse(call: Call, response: Response) = response.close()
                }
                client.newCall(
                    POST(
                        "$baseUrl/tools/submit_ajax.ashx?action=user_check_login",
                        headersBuilder()
                            .set("Referer", manga.url)
                            .set("X-Requested-With", "XMLHttpRequest")
                            .build(),
                    ),
                ).enqueue(callback)

                client.newCall(
                    GET(
                        "$baseUrl/tools/vote.ashx?act=get&bid=$bid",
                        headersBuilder()
                            .set("Referer", manga.url)
                            .set("X-Requested-With", "XMLHttpRequest").build(),
                    ),
                ).enqueue(callback)
            }
        }
        return call
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("div.book-title > h1:nth-child(1)")?.text() ?: ""
            description = document.selectFirst("div#intro-all")?.text() ?: ""
            thumbnail_url = document.selectFirst("p.hcover > img")?.absUrl("src")
            author = document.select("span:contains(漫画作者) > a , span:contains(漫畫作者) > a").text().replace(" ", ", ")
            genre = document.select("span:contains(漫画剧情) > a , span:contains(漫畫劇情) > a").text().replace(" ", ", ")
            status = when (document.selectFirst("div.book-detail > ul.detail-list > li.status > span > span")?.text()) {
                "连载中", "連載中" -> SManga.ONGOING
                "已完结", "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/comic/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val sManga = mangaDetailsParse(response)
        sManga.url = "/comic/$id/"
        return MangasPage(listOf(sManga), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_ID_SEARCH)) {
        val id = query.removePrefix(PREFIX_ID_SEARCH)
        client.newCall(searchMangaByIdRequest(id))
            .asObservableSuccess()
            .map { response -> searchMangaByIdParse(response, id) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        val hiddenEncryptedChapterList = document.selectFirst("#__VIEWSTATE")
        if (hiddenEncryptedChapterList != null) {
            if (getShowR18()) {
                val decodedHiddenChapterList = LZString.decompressFromBase64(hiddenEncryptedChapterList.`val`())
                val hiddenChapterList = Jsoup.parseBodyFragment(decodedHiddenChapterList, response.request.url.toString())

                document.selectFirst("#erroraudit_show")?.replaceWith(hiddenChapterList)
                hiddenEncryptedChapterList.remove()
            } else {
                error("您需要打开R18作品显示开关并重启软件才能阅读此作品")
            }
        }
        val latestChapterHref = document.selectFirst("div.book-detail > ul.detail-list > li.status > span > a.blue")?.attr("href")

        val sectionList = document.select("[id^=chapter-list-]")
        sectionList.forEach { section ->
            val pageList = section.select("ul").reversed()
            pageList.forEach { page ->
                val chapterList = page.select("li > a.status0")
                chapterList.forEach {
                    val currentChapter = SChapter.create()
                    currentChapter.url = it.attr("href")

                    val titleAttr = it.attr("title")
                    currentChapter.name = titleAttr.ifEmpty { it.selectFirst("span")?.ownText() ?: "" }

                    if (currentChapter.url == latestChapterHref) {
                        val dateElement = document.select("div.book-detail > ul.detail-list > li.status > span > span.red").last()
                        if (dateElement != null) {
                            currentChapter.date_upload = dateFormat.tryParse(dateElement.text())
                        }
                    }
                    chapters.add(currentChapter)
                }
            }
        }

        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        if (document.selectFirst("#erroraudit_show") != null && !getShowR18()) {
            error("R18作品显示开关未开启或未生效")
        }

        val html = document.outerHtml()
        val imgCode = packedRegex.find(html)?.groupValues?.get(1)?.let {
            it.replace(packedContentRegex) { match ->
                val lzs = match.groupValues[1]
                val decoded = LZString.decompressFromBase64(lzs)
                "'$decoded'.split('|')"
            }
        } ?: throw Exception("Failed to find image code")

        val imgDecode = Unpacker.unpack(singleQuoteRegex.replace(imgCode, "-"))
        val imgJsonStr = blockCcArgRegex.find(imgDecode)?.groupValues?.get(0) ?: throw Exception("Failed to extract JSON from parsed code")
        val imageJson = imgJsonStr.parseAs<Comic>()

        return imageJson.files.orEmpty().mapIndexed { i, imgStr ->
            val imgurl = "${imageServer[0]}${imageJson.path}$imgStr?e=${imageJson.sl?.e}&m=${imageJson.sl?.m}"
            Page(i, imageUrl = imgurl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", baseUrl)
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
        .set("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).run {
            key = MAINSITE_RATELIMIT_PREF
            title = MAINSITE_RATELIMIT_PREF_TITLE
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            summary = MAINSITE_RATELIMIT_PREF_SUMMARY

            setDefaultValue(MAINSITE_RATELIMIT_DEFAULT_VALUE)
            screen.addPreference(this)
        }

        ListPreference(screen.context).run {
            key = IMAGE_CDN_RATELIMIT_PREF
            title = IMAGE_CDN_RATELIMIT_PREF_TITLE
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY

            setDefaultValue(IMAGE_CDN_RATELIMIT_DEFAULT_VALUE)
            screen.addPreference(this)
        }

        CheckBoxPreference(screen.context).run {
            key = SHOW_ZH_HANT_WEBSITE_PREF
            title = SHOW_ZH_HANT_WEBSITE_PREF_TITLE
            summary = SHOW_ZH_HANT_WEBSITE_PREF_SUMMARY
            screen.addPreference(this)
        }

        CheckBoxPreference(screen.context).run {
            key = SHOW_R18_PREF
            title = SHOW_R18_PREF_TITLE
            summary = SHOW_R18_PREF_SUMMARY
            screen.addPreference(this)
        }

        CheckBoxPreference(screen.context).run {
            key = USE_MIRROR_URL_PREF
            title = USE_MIRROR_URL_PREF_TITLE
            summary = USE_MIRROR_URL_PREF_SUMMARY

            setDefaultValue(false)
            screen.addPreference(this)
        }
    }

    private fun getShowR18(): Boolean = preferences.getBoolean(SHOW_R18_PREF, false)

    override fun getFilterList() = FilterList(
        SortFilter(),
        LocaleFilter(),
        GenreFilter(),
        ReaderFilter(),
        PublishDateFilter(),
        FirstLetterFilter(),
        StatusFilter(),
    )

    companion object {
        private const val SHOW_R18_PREF = "showR18Default"
        private const val SHOW_R18_PREF_TITLE = "显示R18作品"
        private const val SHOW_R18_PREF_SUMMARY = "请确认您的IP不在漫画柜的屏蔽列表内，例如中国大陆IP。需要重启软件以生效。\n开启后如需关闭，需要到Tachiyomi高级设置内清除Cookies后才能生效。"

        private const val SHOW_ZH_HANT_WEBSITE_PREF = "showZhHantWebsite"
        private const val SHOW_ZH_HANT_WEBSITE_PREF_TITLE = "使用繁体版网站"
        private const val SHOW_ZH_HANT_WEBSITE_PREF_SUMMARY = "需要重启软件以生效。"

        private const val USE_MIRROR_URL_PREF = "useMirrorWebsitePreference"
        private const val USE_MIRROR_URL_PREF_TITLE = "使用镜像网址"
        private const val USE_MIRROR_URL_PREF_SUMMARY = "使用镜像网址: mhgui.com，部分漫画可能无法观看。"

        private const val MAINSITE_RATELIMIT_PREF = "mainSiteRatelimitPreference"
        private const val MAINSITE_RATELIMIT_PREF_TITLE = "主站每十秒连接数限制"
        private const val MAINSITE_RATELIMIT_PREF_SUMMARY = "此值影响更新书架时发起连接请求的数量。调低此值可能减小IP被屏蔽的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s"
        private const val MAINSITE_RATELIMIT_DEFAULT_VALUE = "10"

        private const val IMAGE_CDN_RATELIMIT_PREF = "imgCDNRatelimitPreference"
        private const val IMAGE_CDN_RATELIMIT_PREF_TITLE = "图片CDN每秒连接数限制"
        private const val IMAGE_CDN_RATELIMIT_PREF_SUMMARY = "此值影响加载图片时发起连接请求的数量。调低此值可能减小IP被屏蔽的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s"
        private const val IMAGE_CDN_RATELIMIT_DEFAULT_VALUE = "4"

        const val RANK_PREFIX = "rank_"
        const val PREFIX_ID_SEARCH = "id:"

        private val ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()

        @Suppress("RegExpRedundantEscape")
        private val packedRegex = Regex("""window\[".*?"\](\(.*\)\s*\{[\s\S]+\}\s*\(.*\))""")

        @Suppress("RegExpRedundantEscape")
        private val blockCcArgRegex = Regex("""\{.*\}""")

        private val packedContentRegex = Regex("""['"]([0-9A-Za-z+/=]+)['"]\[['"].*?['"]]\(['"].*?['"]\)""")

        private val singleQuoteRegex = Regex("""\\'""")
    }
}
