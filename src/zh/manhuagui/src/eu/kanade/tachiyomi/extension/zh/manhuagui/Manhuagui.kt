package eu.kanade.tachiyomi.extension.zh.manhuagui

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.lib.lzstring.LZString
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class Manhuagui(
    override val name: String = "漫画柜",
    override val lang: String = "zh",
) : ConfigurableSource, ParsedHttpSource() {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

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
    private val json: Json by injectLazy()
    private val baseHttpUrl: HttpUrl = baseUrl.toHttpUrl()

    // Add rate limit to fix manga thumbnail load failure
    override val client: OkHttpClient =
        if (getShowR18()) {
            network.client.newBuilder()
                .rateLimitHost(baseHttpUrl, preferences.getString(MAINSITE_RATELIMIT_PREF, MAINSITE_RATELIMIT_DEFAULT_VALUE)!!.toInt(), 10)
                .rateLimitHost(imageServer[0].toHttpUrl(), preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_DEFAULT_VALUE)!!.toInt())
                .rateLimitHost(imageServer[1].toHttpUrl(), preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_DEFAULT_VALUE)!!.toInt())
                .addNetworkInterceptor(AddCookieHeaderInterceptor(baseHttpUrl.host))
                .build()
        } else {
            network.client.newBuilder()
                .rateLimitHost(baseHttpUrl, preferences.getString(MAINSITE_RATELIMIT_PREF, MAINSITE_RATELIMIT_DEFAULT_VALUE)!!.toInt(), 10)
                .rateLimitHost(imageServer[0].toHttpUrl(), preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_DEFAULT_VALUE)!!.toInt())
                .rateLimitHost(imageServer[1].toHttpUrl(), preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_DEFAULT_VALUE)!!.toInt())
                .build()
        }

    // Add R18 verification cookie
    class AddCookieHeaderInterceptor(private val baseHost: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            if (chain.request().url.host == baseHost) {
                val originalCookies = chain.request().header("Cookie") ?: ""
                if (originalCookies != "" && !originalCookies.contains("isAdult=1")) {
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
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list/update_p$page.html", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query != "") {
            // Normal search
            return GET("$baseUrl/s/${query}_p$page.html", headers)
        } else {
            // Filters search
            val params = filters.map {
                if (it !is SortFilter && it is UriPartFilter) {
                    it.toUriPart()
                } else {
                    ""
                }
            }.filter { it != "" }.joinToString("_")

            val sortOrder = filters.filterIsInstance<SortFilter>()
                .joinToString("") {
                    (it as UriPartFilter).toUriPart()
                }

            // Example: https://www.manhuagui.com/list/japan_maoxian_qingnian_2020_b/update_p1.html
            //                                        /$params                      /$sortOrder $page
            var url = "$baseUrl/list"
            if (params != "") {
                url += "/$params"
            }
            url += if (sortOrder == "") {
                "/index_p$page.html"
            } else {
                "/${sortOrder}_p$page.html"
            }
            return GET(url, headers)
        }
    }

    // Return mobile webpage url to "Open in browser" and "Share manga".
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(mobileWebsiteUrl + manga.url)
    }

    // Bypass mangaDetailsRequest
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val call = client.newCall(GET(baseUrl + manga.url, headers))
        val bid = Regex("""\d+""").find(manga.url)?.value
        if (bid != null) {
            // Send a get request to https://www.manhuagui.com/tools/vote.ashx?act=get&bid=$bid
            // and a post request to https://www.manhuagui.com/tools/submit_ajax.ashx?action=user_check_login
            // to simulate what web page javascript do and get "country" cookie.
            // Send requests using coroutine in another (IO) thread.
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    // Delay 1 second to wait main manga details request complete
                    delay(1000L)
                    client.newCall(
                        POST(
                            "$baseUrl/tools/submit_ajax.ashx?action=user_check_login",
                            headersBuilder()
                                .set("Referer", manga.url)
                                .set("X-Requested-With", "XMLHttpRequest")
                                .build(),
                        ),
                    ).enqueue(
                        object : Callback {
                            override fun onFailure(call: Call, e: IOException) = e.printStackTrace()
                            override fun onResponse(call: Call, response: Response) = response.close()
                        },
                    )

                    client.newCall(
                        GET(
                            "$baseUrl/tools/vote.ashx?act=get&bid=$bid",
                            headersBuilder()
                                .set("Referer", manga.url)
                                .set("X-Requested-With", "XMLHttpRequest").build(),
                        ),
                    ).enqueue(
                        object : Callback {
                            override fun onFailure(call: Call, e: IOException) = e.printStackTrace()
                            override fun onResponse(call: Call, response: Response) = response.close()
                        },
                    )
                }
            }
        }
        return call
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    // For ManhuaguiUrlActivity
    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/comic/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val sManga = mangaDetailsParse(response)
        sManga.url = "/comic/$id/"
        return MangasPage(listOf(sManga), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (response.request.url.encodedPath.startsWith("/s/")) {
            // Normal search
            val mangas = document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }
            val hasNextPage = searchMangaNextPageSelector().let { selector ->
                document.select(selector).first()
            } != null

            return MangasPage(mangas, hasNextPage)
        } else {
            // Filters search
            val mangas = document.select(popularMangaSelector()).map { element ->
                popularMangaFromElement(element)
            }
            val hasNextPage = document.select(popularMangaNextPageSelector()).first() != null
            return MangasPage(mangas, hasNextPage)
        }
    }

    override fun popularMangaSelector() = "ul#contList > li"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = "div.book-result > ul > li"
    override fun chapterListSelector() = "ul > li > a.status0"

    override fun searchMangaNextPageSelector() = "span.current + a" // "a.prev" contain 2~4 elements: first, previous, next and last page, "span.current + a" is a better choice.
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", baseUrl)
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.105 Safari/537.36")
        .set("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.bcover").first()!!.let {
            manga.url = it.attr("href")
            manga.title = it.attr("title").trim()

            // Fix thumbnail lazy load
            val thumbnailElement = it.select("img").first()!!
            manga.thumbnail_url = if (thumbnailElement.hasAttr("src")) {
                thumbnailElement.attr("abs:src")
            } else {
                thumbnailElement.attr("abs:data-src")
            }
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("div.book-detail").first()!!.let {
            manga.url = it.select("dl > dt > a").first()!!.attr("href")
            manga.title = it.select("dl > dt > a").first()!!.attr("title").trim()
            manga.thumbnail_url = element.select("div.book-cover > a.bcover > img").first()!!.attr("abs:src")
        }

        return manga
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        // Try to get R18 manga hidden chapter list
        val hiddenEncryptedChapterList = document.select("#__VIEWSTATE").first()
        if (hiddenEncryptedChapterList != null) {
            if (getShowR18()) {
                // Hidden chapter list is LZString encoded
                val decodedHiddenChapterList = LZString.decompressFromBase64(hiddenEncryptedChapterList.`val`())
                val hiddenChapterList = Jsoup.parse(decodedHiddenChapterList, response.request.url.toString())

                // Replace R18 warning with actual chapter list
                document.select("#erroraudit_show").first()!!.replaceWith(hiddenChapterList)
                // Remove hidden chapter list element
                document.select("#__VIEWSTATE").first()!!.remove()
            } else {
                // "You need to enable R18 switch and restart Tachiyomi to read this manga"
                error("您需要打开R18作品显示开关并重启软件才能阅读此作品")
            }
        }
        val latestChapterHref = document.select("div.book-detail > ul.detail-list > li.status > span > a.blue").first()?.attr("href")
        val chNumRegex = Regex("""\d+""")

        val sectionList = document.select("[id^=chapter-list-]")
        sectionList.forEach { section ->
            val pageList = section.select("ul")
            pageList.reverse()
            pageList.forEach { page ->
                val pageChapters = mutableListOf<SChapter>()
                val chapterList = page.select("li > a.status0")
                chapterList.forEach {
                    val currentChapter = SChapter.create()
                    currentChapter.url = it.attr("href")
                    currentChapter.name = it?.attr("title")?.trim() ?: it.select("span").first()!!.ownText()
                    currentChapter.chapter_number = chNumRegex.find(currentChapter.name)?.value?.toFloatOrNull() ?: -1F

                    // Manhuagui only provide upload date for latest chapter
                    if (currentChapter.url == latestChapterHref) {
                        currentChapter.date_upload = parseDate(document.select("div.book-detail > ul.detail-list > li.status > span > span.red").last()!!)
                    }
                    pageChapters.add(currentChapter)
                }

                chapters.addAll(pageChapters)
            }
        }

        return chapters
    }

    private fun parseDate(element: Element): Long = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(element.text())?.time ?: 0

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        /**
         * When searching manga from intent filter, sometimes will cause the error below and manga don't appear in search result:
         *   eu.kanade.tachiyomi.debug E/GlobalSearchPresenter$search: kotlin.UninitializedPropertyAccessException: lateinit property title has not been initialized
         *      at eu.kanade.tachiyomi.source.model.SMangaImpl.getTitle(SMangaImpl.kt:7)
         *      at eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchPresenter.networkToLocalManga(GlobalSearchPresenter.kt:259)
         *      at eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchPresenter$search$1$4.call(GlobalSearchPresenter.kt:172)
         *      at eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchPresenter$search$1$4.call(GlobalSearchPresenter.kt:34)
         * Parse manga.title here can solve it.
         */
        manga.title = document.select("div.book-title > h1:nth-child(1)").text().trim()
        manga.description = document.select("div#intro-all").text().trim()
        manga.thumbnail_url = document.select("p.hcover > img").attr("abs:src")
        manga.author = document.select("span:contains(漫画作者) > a , span:contains(漫畫作者) > a").text().trim().replace(" ", ", ")
        manga.genre = document.select("span:contains(漫画剧情) > a , span:contains(漫畫劇情) > a").text().trim().replace(" ", ", ")
        manga.status = when (document.select("div.book-detail > ul.detail-list > li.status > span > span").first()!!.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        return manga
    }

    // Page list is inside [packed](http://dean.edwards.name/packer/) JavaScript with a special twist:
    // the normal content array (`'a|b|c'.split('|')`) is replaced with LZString and base64-encoded
    // version.
    //
    // These "\" can't be remove: "\}", more info in pull request 3926.
    @Suppress("RegExpRedundantEscape")
    private val packedRegex = Regex("""window\[".*?"\](\(.*\)\s*\{[\s\S]+\}\s*\(.*\))""")

    @Suppress("RegExpRedundantEscape")
    private val blockCcArgRegex = Regex("""\{.*\}""")

    private val packedContentRegex = Regex("""['"]([0-9A-Za-z+/=]+)['"]\[['"].*?['"]]\(['"].*?['"]\)""")

    override fun pageListParse(document: Document): List<Page> {
        // R18 warning element (#erroraudit_show) is remove by web page javascript, so here the warning element
        // will always exist if this manga is R18 limited whether R18 verification cookies has been sent or not.
        // But it will not interfere parse mechanism below.
        if (document.select("#erroraudit_show").first() != null && !getShowR18()) {
            error("R18作品显示开关未开启或未生效") // "R18 setting didn't enabled or became effective"
        }

        val html = document.html()
        val imgCode = packedRegex.find(html)!!.groupValues[1].let {
            // Make the packed content normal again so :lib:unpacker can do its job
            it.replace(packedContentRegex) { match ->
                val lzs = match.groupValues[1]
                val decoded = LZString.decompressFromBase64(lzs).replace("'", "\\'")

                "'$decoded'.split('|')"
            }
        }
        val imgDecode = Unpacker.unpack(imgCode)

        val imgJsonStr = blockCcArgRegex.find(imgDecode)!!.groupValues[0]
        val imageJson: Comic = json.decodeFromString(imgJsonStr)

        return imageJson.files!!.mapIndexed { i, imgStr ->
            val imgurl = "${imageServer[0]}${imageJson.path}$imgStr?e=${imageJson.sl?.e}&m=${imageJson.sl?.m}"
            Page(i, "", imgurl)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val mainSiteRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = MAINSITE_RATELIMIT_PREF
            title = MAINSITE_RATELIMIT_PREF_TITLE
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            summary = MAINSITE_RATELIMIT_PREF_SUMMARY

            setDefaultValue(MAINSITE_RATELIMIT_DEFAULT_VALUE)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(MAINSITE_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val imgCDNRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = IMAGE_CDN_RATELIMIT_PREF
            title = IMAGE_CDN_RATELIMIT_PREF_TITLE
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY

            setDefaultValue(IMAGE_CDN_RATELIMIT_DEFAULT_VALUE)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(IMAGE_CDN_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        // Simplified/Traditional Chinese version website switch
        val zhHantPreference = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = SHOW_ZH_HANT_WEBSITE_PREF
            title = SHOW_ZH_HANT_WEBSITE_PREF_TITLE
            summary = SHOW_ZH_HANT_WEBSITE_PREF_SUMMARY

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putBoolean(SHOW_ZH_HANT_WEBSITE_PREF, newValue as Boolean).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        // R18+ switch
        val r18Preference = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = SHOW_R18_PREF
            title = SHOW_R18_PREF_TITLE
            summary = SHOW_R18_PREF_SUMMARY

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val newSetting = preferences.edit().putBoolean(SHOW_R18_PREF, newValue as Boolean).commit()
                    newSetting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val mirrorURLPreference = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = USE_MIRROR_URL_PREF
            title = USE_MIRROR_URL_PREF_TITLE
            summary = USE_MIRROR_URL_PREF_SUMMARY

            setDefaultValue(false)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val newSetting = preferences.edit().putBoolean(USE_MIRROR_URL_PREF, newValue as Boolean).commit()
                    newSetting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(mainSiteRateLimitPreference)
        screen.addPreference(imgCDNRateLimitPreference)
        screen.addPreference(zhHantPreference)
        screen.addPreference(r18Preference)
        screen.addPreference(mirrorURLPreference)
    }

    private fun getShowR18(): Boolean = preferences.getBoolean(SHOW_R18_PREF, false)

    private open class UriPartFilter(
        displayName: String,
        val pair: Array<Pair<String, String>>,
        defaultState: Int = 0,
    ) : Filter.Select<String>(displayName, pair.map { it.first }.toTypedArray(), defaultState) {
        open fun toUriPart() = pair[state].second
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        LocaleFilter(),
        GenreFilter(),
        ReaderFilter(),
        PublishDateFilter(),
        FirstLetterFilter(),
        StatusFilter(),
    )

    private class SortFilter : UriPartFilter(
        "排序方式",
        arrayOf(
            Pair("人气最旺", "view"), // Same to popularMangaRequest()
            Pair("最新发布", ""), // Publish date
            Pair("最新更新", "update"),
            Pair("评分最高", "rate"),
        ),
    )

    private class LocaleFilter : UriPartFilter(
        "按地区",
        arrayOf(
            Pair("全部", ""), // all
            Pair("日本", "japan"),
            Pair("港台", "hongkong"),
            Pair("其它", "other"),
            Pair("欧美", "europe"),
            Pair("内地", "china"),
            Pair("韩国", "korea"),
        ),
    )

    private class GenreFilter : UriPartFilter(
        "按剧情",
        arrayOf(
            Pair("全部", ""),
            Pair("热血", "rexue"),
            Pair("冒险", "maoxian"),
            Pair("魔幻", "mohuan"),
            Pair("神鬼", "shengui"),
            Pair("搞笑", "gaoxiao"),
            Pair("萌系", "mengxi"),
            Pair("爱情", "aiqing"),
            Pair("科幻", "kehuan"),
            Pair("魔法", "mofa"),
            Pair("格斗", "gedou"),
            Pair("武侠", "wuxia"),
            Pair("机战", "jizhan"),
            Pair("战争", "zhanzheng"),
            Pair("竞技", "jingji"),
            Pair("体育", "tiyu"),
            Pair("校园", "xiaoyuan"),
            Pair("生活", "shenghuo"),
            Pair("励志", "lizhi"),
            Pair("历史", "lishi"),
            Pair("伪娘", "weiniang"),
            Pair("宅男", "zhainan"),
            Pair("腐女", "funv"),
            Pair("耽美", "danmei"),
            Pair("百合", "baihe"),
            Pair("后宫", "hougong"),
            Pair("治愈", "zhiyu"),
            Pair("美食", "meishi"),
            Pair("推理", "tuili"),
            Pair("悬疑", "xuanyi"),
            Pair("恐怖", "kongbu"),
            Pair("四格", "sige"),
            Pair("职场", "zhichang"),
            Pair("侦探", "zhentan"),
            Pair("社会", "shehui"),
            Pair("音乐", "yinyue"),
            Pair("舞蹈", "wudao"),
            Pair("杂志", "zazhi"),
            Pair("黑道", "heidao"),
        ),
    )

    private class ReaderFilter : UriPartFilter(
        "按受众",
        arrayOf(
            Pair("全部", ""),
            Pair("少女", "shaonv"),
            Pair("少年", "shaonian"),
            Pair("青年", "qingnian"),
            Pair("儿童", "ertong"),
            Pair("通用", "tongyong"),
        ),
    )

    private class PublishDateFilter : UriPartFilter(
        "按年份",
        arrayOf(
            Pair("全部", ""),
            Pair("2020年", "2020"),
            Pair("2019年", "2019"),
            Pair("2018年", "2018"),
            Pair("2017年", "2017"),
            Pair("2016年", "2016"),
            Pair("2015年", "2015"),
            Pair("2014年", "2014"),
            Pair("2013年", "2013"),
            Pair("2012年", "2012"),
            Pair("2011年", "2011"),
            Pair("2010年", "2010"),
            Pair("00年代", "200x"),
            Pair("90年代", "199x"),
            Pair("80年代", "198x"),
            Pair("更早", "197x"),
        ),
    )

    private class FirstLetterFilter : UriPartFilter(
        "按字母",
        arrayOf(
            Pair("全部", ""),
            Pair("A", "a"),
            Pair("B", "b"),
            Pair("C", "c"),
            Pair("D", "d"),
            Pair("E", "e"),
            Pair("F", "f"),
            Pair("G", "g"),
            Pair("H", "h"),
            Pair("I", "i"),
            Pair("J", "j"),
            Pair("K", "k"),
            Pair("L", "l"),
            Pair("M", "m"),
            Pair("N", "n"),
            Pair("O", "o"),
            Pair("P", "p"),
            Pair("Q", "q"),
            Pair("R", "r"),
            Pair("S", "s"),
            Pair("T", "t"),
            Pair("U", "u"),
            Pair("V", "v"),
            Pair("W", "w"),
            Pair("X", "x"),
            Pair("Y", "y"),
            Pair("Z", "z"),
            Pair("0-9", "0-9"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "按进度",
        arrayOf(
            Pair("全部", ""),
            Pair("连载", "lianzai"),
            Pair("完结", "wanjie"),
        ),
    )

    companion object {
        private const val SHOW_R18_PREF = "showR18Default"
        private const val SHOW_R18_PREF_TITLE = "显示R18作品" // "Show R18 contents"
        private const val SHOW_R18_PREF_SUMMARY = "请确认您的IP不在漫画柜的屏蔽列表内，例如中国大陆IP。需要重启软件以生效。\n开启后如需关闭，需要到Tachiyomi高级设置内清除Cookies后才能生效。" // "Please make sure your IP is not in Manhuagui's ban list, e.g., China mainland IP. Tachiyomi restart required. If you want to close this switch after enabled it, you need to clear cookies in Tachiyomi advanced setting too.

        private const val SHOW_ZH_HANT_WEBSITE_PREF = "showZhHantWebsite"
        private const val SHOW_ZH_HANT_WEBSITE_PREF_TITLE = "使用繁体版网站" // "Use traditional chinese version website"
        private const val SHOW_ZH_HANT_WEBSITE_PREF_SUMMARY = "需要重启软件以生效。" // "You need to restart Tachiyomi"

        private const val USE_MIRROR_URL_PREF = "useMirrorWebsitePreference"
        private const val USE_MIRROR_URL_PREF_TITLE = "使用镜像网址"
        private const val USE_MIRROR_URL_PREF_SUMMARY = "使用镜像网址: mhgui.com，部分漫画可能无法观看。" // "Use mirror url. Some manga may be hidden."

        private const val MAINSITE_RATELIMIT_PREF = "mainSiteRatelimitPreference"
        private const val MAINSITE_RATELIMIT_PREF_TITLE = "主站每十秒连接数限制" // "Ratelimit permits per 10 seconds for main website"
        private const val MAINSITE_RATELIMIT_PREF_SUMMARY = "此值影响更新书架时发起连接请求的数量。调低此值可能减小IP被屏蔽的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s" // "This value affects network request amount for updating library. Lower this value may reduce the chance to get IP Ban, but loading speed will be slower too. Tachiyomi restart required."
        private const val MAINSITE_RATELIMIT_DEFAULT_VALUE = "10"

        private const val IMAGE_CDN_RATELIMIT_PREF = "imgCDNRatelimitPreference"
        private const val IMAGE_CDN_RATELIMIT_PREF_TITLE = "图片CDN每秒连接数限制" // "Ratelimit permits per second for image CDN"
        private const val IMAGE_CDN_RATELIMIT_PREF_SUMMARY = "此值影响加载图片时发起连接请求的数量。调低此值可能减小IP被屏蔽的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s" // "This value affects network request amount for loading image. Lower this value may reduce the chance to get IP Ban, but loading speed will be slower too. Tachiyomi restart required."
        private const val IMAGE_CDN_RATELIMIT_DEFAULT_VALUE = "4"

        private val ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()
        const val PREFIX_ID_SEARCH = "id:"
    }
}
