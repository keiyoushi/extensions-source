package eu.kanade.tachiyomi.extension.zh.manhuagui

import android.app.Application
import android.content.SharedPreferences
import app.cash.quickjs.QuickJs
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
                val decodedHiddenChapterList = QuickJs.create().use {
                    it.evaluate(
                        jsDecodeFunc +
                            """LZString.decompressFromBase64('${hiddenEncryptedChapterList.`val`()}');""",
                    ) as String
                }
                val hiddenChapterList = Jsoup.parse(decodedHiddenChapterList, response.request.url.toString())
                // Replace R18 warning with actual chapter list
                document.select("#erroraudit_show").first()!!.replaceWith(hiddenChapterList)
                // Remove hidden chapter list element
                document.select("#__VIEWSTATE").first()!!.remove()
            } else {
                // "You need to enable R18 switch and restart Tachiyomi to read this manga"
                error(R18_NEED_ENABLE)
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
                    currentChapter.name = it.attr("title").trim().ifEmpty { it.select("span").first()!!.ownText() }
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
        manga.genre = document.select("span:contains(漫画剧情) > a , span:contains(漫畫劇情) > a").text().trim()
            .split(' ').joinToString(transform = ::translateGenre)
        manga.status = when (document.select("div.book-detail > ul.detail-list > li.status > span > span").first()?.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        return manga
    }

    private val jsDecodeFunc =
        """
        var LZString=(function(){var f=String.fromCharCode;var keyStrBase64="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";var baseReverseDic={};function getBaseValue(alphabet,character){if(!baseReverseDic[alphabet]){baseReverseDic[alphabet]={};for(var i=0;i<alphabet.length;i++){baseReverseDic[alphabet][alphabet.charAt(i)]=i}}return baseReverseDic[alphabet][character]}var LZString={decompressFromBase64:function(input){if(input==null)return"";if(input=="")return null;return LZString._0(input.length,32,function(index){return getBaseValue(keyStrBase64,input.charAt(index))})},_0:function(length,resetValue,getNextValue){var dictionary=[],next,enlargeIn=4,dictSize=4,numBits=3,entry="",result=[],i,w,bits,resb,maxpower,power,c,data={val:getNextValue(0),position:resetValue,index:1};for(i=0;i<3;i+=1){dictionary[i]=i}bits=0;maxpower=Math.pow(2,2);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}switch(next=bits){case 0:bits=0;maxpower=Math.pow(2,8);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}c=f(bits);break;case 1:bits=0;maxpower=Math.pow(2,16);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}c=f(bits);break;case 2:return""}dictionary[3]=c;w=c;result.push(c);while(true){if(data.index>length){return""}bits=0;maxpower=Math.pow(2,numBits);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}switch(c=bits){case 0:bits=0;maxpower=Math.pow(2,8);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}dictionary[dictSize++]=f(bits);c=dictSize-1;enlargeIn--;break;case 1:bits=0;maxpower=Math.pow(2,16);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}dictionary[dictSize++]=f(bits);c=dictSize-1;enlargeIn--;break;case 2:return result.join('')}if(enlargeIn==0){enlargeIn=Math.pow(2,numBits);numBits++}if(dictionary[c]){entry=dictionary[c]}else{if(c===dictSize){entry=w+w.charAt(0)}else{return null}}result.push(entry);dictionary[dictSize++]=w+entry.charAt(0);enlargeIn--;w=entry;if(enlargeIn==0){enlargeIn=Math.pow(2,numBits);numBits++}}}};return LZString})();String.prototype.splic=function(f){return LZString.decompressFromBase64(this).split(f)};
    """

    // Page list is javascript eval encoded and LZString encoded, these website:
    // http://www.oicqzone.com/tool/eval/ , https://www.w3xue.com/tools/jseval/ ,
    // https://www.w3cschool.cn/tools/index?name=evalencode can try to decode javascript eval encoded content,
    // jsDecodeFunc's LZString.decompressFromBase64() can decode LZString.

    // These "\" can't be remove: "\}", more info in pull request 3926.
    @Suppress("RegExpRedundantEscape")
    private val re = Regex("""window\[".*?"\](\(.*\)\s*\{[\s\S]+\}\s*\(.*\))""")

    @Suppress("RegExpRedundantEscape")
    private val re2 = Regex("""\{.*\}""")

    override fun pageListParse(document: Document): List<Page> {
        // R18 warning element (#erroraudit_show) is remove by web page javascript, so here the warning element
        // will always exist if this manga is R18 limited whether R18 verification cookies has been sent or not.
        // But it will not interfere parse mechanism below.
        if (document.select("#erroraudit_show").first() != null && !getShowR18()) {
            error(R18_NOT_EFFECTIVE) // "R18 setting didn't enabled or became effective"
        }

        val html = document.html()
        val imgCode = re.find(html)?.groups?.get(1)?.value
        val imgDecode = QuickJs.create().use {
            it.evaluate(jsDecodeFunc + imgCode) as String
        }

        val imgJsonStr = re2.find(imgDecode)?.groups?.get(0)?.value
        val imageJson: Comic = json.decodeFromString(imgJsonStr!!)

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
        SortFilter(
            SORT_BY,
            arrayOf(
                Pair(SORT_BY_POPULAR, "view"), // Same to popularMangaRequest()
                Pair(SORT_BY_RELEASE, ""), // Publish date
                Pair(SORT_BY_UPDATE, "update"),
                Pair(SORT_BY_RATE, "rate"),
            ),
        ),
        LocaleFilter(
            BY_REGION,
            arrayOf(
                Pair(REGION_ALL, ""), // all
                Pair(REGION_JAPAN, "japan"),
                Pair(REGION_HONGKONG, "hongkong"),
                Pair(REGION_OTHER, "other"),
                Pair(REGION_EUROPE, "europe"),
                Pair(REGION_CHINA, "china"),
                Pair(REGION_KOREA, "korea"),
            ),
        ),
        GenreFilter(
            BY_GENRE,
            arrayOf(
                Pair(GENRE_ALL, ""),
                Pair(GENRE_rexue, "rexue"),
                Pair(GENRE_maoxian, "maoxian"),
                Pair(GENRE_mohuan, "mohuan"),
                Pair(GENRE_shengui, "shengui"),
                Pair(GENRE_gaoxiao, "gaoxiao"),
                Pair(GENRE_mengxi, "mengxi"),
                Pair(GENRE_aiqing, "aiqing"),
                Pair(GENRE_kehuan, "kehuan"),
                Pair(GENRE_mofa, "mofa"),
                Pair(GENRE_gedou, "gedou"),
                Pair(GENRE_wuxia, "wuxia"),
                Pair(GENRE_jizhan, "jizhan"),
                Pair(GENRE_zhanzheng, "zhanzheng"),
                Pair(GENRE_jingji, "jingji"),
                Pair(GENRE_tiyu, "tiyu"),
                Pair(GENRE_xiaoyuan, "xiaoyuan"),
                Pair(GENRE_shenghuo, "shenghuo"),
                Pair(GENRE_lizhi, "lizhi"),
                Pair(GENRE_lishi, "lishi"),
                Pair(GENRE_weiniang, "weiniang"),
                Pair(GENRE_zhainan, "zhainan"),
                Pair(GENRE_funv, "funv"),
                Pair(GENRE_danmei, "danmei"),
                Pair(GENRE_baihe, "baihe"),
                Pair(GENRE_hougong, "hougong"),
                Pair(GENRE_zhiyu, "zhiyu"),
                Pair(GENRE_meishi, "meishi"),
                Pair(GENRE_tuili, "tuili"),
                Pair(GENRE_xuanyi, "xuanyi"),
                Pair(GENRE_kongbu, "kongbu"),
                Pair(GENRE_sige, "sige"),
                Pair(GENRE_zhichang, "zhichang"),
                Pair(GENRE_zhentan, "zhentan"),
                Pair(GENRE_shehui, "shehui"),
                Pair(GENRE_yinyue, "yinyue"),
                Pair(GENRE_wudao, "wudao"),
                Pair(GENRE_zazhi, "zazhi"),
                Pair(GENRE_heidao, "heidao"),
            ),
        ),
        ReaderFilter(
            BY_AUDIENCE,
            arrayOf(
                Pair(AUDIENCE_ALL, ""),
                Pair(AUDIENCE_shaonv, "shaonv"),
                Pair(AUDIENCE_shaonian, "shaonian"),
                Pair(AUDIENCE_qingnian, "qingnian"),
                Pair(AUDIENCE_ertong, "ertong"),
                Pair(AUDIENCE_tongyong, "tongyong"),
            ),
        ),
        PublishDateFilter(
            BY_YEAR,
            arrayOf(
                Pair(YEAR_ALL, ""),
                Pair(YEAR_2020, "2020"),
                Pair(YEAR_2019, "2019"),
                Pair(YEAR_2018, "2018"),
                Pair(YEAR_2017, "2017"),
                Pair(YEAR_2016, "2016"),
                Pair(YEAR_2015, "2015"),
                Pair(YEAR_2014, "2014"),
                Pair(YEAR_2013, "2013"),
                Pair(YEAR_2012, "2012"),
                Pair(YEAR_2011, "2011"),
                Pair(YEAR_2010, "2010"),
                Pair(YEAR_200x, "200x"),
                Pair(YEAR_199x, "199x"),
                Pair(YEAR_198x, "198x"),
                Pair(YEAR_197x, "197x"),
            ),
        ),
        FirstLetterFilter(
            BY_FIRST_LETER,
            arrayOf(
                Pair(FIRST_LETTER_ALL, ""),
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
        ),
        StatusFilter(
            BY_PROGRESS,
            arrayOf(
                Pair(PROGRESS_ALL, ""),
                Pair(PROGRESS_ONGOING, "lianzai"),
                Pair(PROGRESS_COMPLETED, "wanjie"),
            ),
        ),
    )

    private class SortFilter(
        displayName: String,
        pairs: Array<Pair<String, String>>,
    ) : UriPartFilter(displayName, pairs)

    private class LocaleFilter(
        displayName: String,
        pairs: Array<Pair<String, String>>,
    ) : UriPartFilter(displayName, pairs)

    private class GenreFilter(
        displayName: String,
        pairs: Array<Pair<String, String>>,
    ) : UriPartFilter(displayName, pairs)

    private class ReaderFilter(
        displayName: String,
        pairs: Array<Pair<String, String>>,
    ) : UriPartFilter(displayName, pairs)

    private class PublishDateFilter(
        displayName: String,
        pairs: Array<Pair<String, String>>,
    ) : UriPartFilter(displayName, pairs)

    private class FirstLetterFilter(
        displayName: String,
        pairs: Array<Pair<String, String>>,
    ) : UriPartFilter(displayName, pairs)

    private class StatusFilter(
        displayName: String,
        pairs: Array<Pair<String, String>>,
    ) : UriPartFilter(displayName, pairs)

    private val isChinese = Locale.getDefault().equals("zh")

    private val SHOW_R18_PREF_TITLE = if (isChinese) "显示R18作品" else "Show R18 contents"
    private val SHOW_R18_PREF_SUMMARY = if (isChinese) {
        "请确认您的IP不在漫画柜的屏蔽列表内，例如中国大陆IP。需要重启软件以生效。\n开启后如需关闭，需要到Tachiyomi高级设置内清除Cookies后才能生效。"
    } else {
        "Please make sure your IP is not in Manhuagui's ban list, e.g., China mainland IP. Tachiyomi restart required. If you want to close this switch after enabled it, you need to clear cookies in Tachiyomi advanced setting too."
    }

    private val SHOW_ZH_HANT_WEBSITE_PREF_TITLE = if (isChinese) "使用繁体版网站" else "Use traditional chinese version website"
    private val SHOW_ZH_HANT_WEBSITE_PREF_SUMMARY = if (isChinese) "需要重启软件以生效。" else "You need to restart Tachiyomi"

    private val USE_MIRROR_URL_PREF_TITLE = if (isChinese) "使用镜像网址" else "Use mirror URL"
    private val USE_MIRROR_URL_PREF_SUMMARY = if (isChinese) "使用镜像网址: mhgui.com，部分漫画可能无法观看。" else "Use mirror url. Some manga may be hidden."

    private val MAINSITE_RATELIMIT_PREF_TITLE = if (isChinese) "主站每十秒连接数限制" else "Ratelimit permits per 10 seconds for main website"
    private val MAINSITE_RATELIMIT_PREF_SUMMARY = if (isChinese) {
        "此值影响更新书架时发起连接请求的数量。调低此值可能减小IP被屏蔽的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s"
    } else {
        "This value affects network request amount for updating library. Lower this value may reduce the chance to get IP Ban, but loading speed will be slower too. Tachiyomi restart required."
    }

    private val IMAGE_CDN_RATELIMIT_PREF_TITLE = if (isChinese) "图片CDN每秒连接数限制" else "Ratelimit permits per second for image CDN"
    private val IMAGE_CDN_RATELIMIT_PREF_SUMMARY = if (isChinese) {
        "此值影响加载图片时发起连接请求的数量。调低此值可能减小IP被屏蔽的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s"
    } else {
        "This value affects network request amount for loading image. Lower this value may reduce the chance to get IP Ban, but loading speed will be slower too. Tachiyomi restart required."
    }

    private val R18_NEED_ENABLE = if (isChinese) "您需要打开R18作品显示开关并重启软件才能阅读此作品" else "You need to enable R18 switch and restart Tachiyomi to read this manga"
    private val R18_NOT_EFFECTIVE = if (isChinese) "R18作品显示开关未开启或未生效" else "R18 setting didn't enabled or became effective"

    private val BY_PROGRESS = if (isChinese) "按进度" else "By progress"
    private val PROGRESS_ALL = if (isChinese) "全部" else "All"
    private val PROGRESS_ONGOING = if (isChinese) "连载" else "Ongoing"
    private val PROGRESS_COMPLETED = if (isChinese) "完结" else "Completed"

    private val SORT_BY = if (isChinese) "排序方式" else "Sort by"
    private val SORT_BY_POPULAR = if (isChinese) "人气最旺" else "Most view"
    private val SORT_BY_RELEASE = if (isChinese) "最新发布" else "Latest release"
    private val SORT_BY_UPDATE = if (isChinese) "最新更新" else "Latest update"
    private val SORT_BY_RATE = if (isChinese) "评分最高" else "Most rate"

    private val BY_REGION = if (isChinese) "按地区" else "By region"
    private val REGION_ALL = if (isChinese) "全部" else "All"
    private val REGION_JAPAN = if (isChinese) "日本" else "Japan"
    private val REGION_HONGKONG = if (isChinese) "港台" else "Hongkong"
    private val REGION_OTHER = if (isChinese) "其它" else "Other"
    private val REGION_EUROPE = if (isChinese) "欧美" else "Europe"
    private val REGION_CHINA = if (isChinese) "内地" else "China"
    private val REGION_KOREA = if (isChinese) "韩国" else "Korea"

    private val BY_GENRE = if (isChinese) "按剧情" else "By genre"
    private val GENRE_ALL = if (isChinese) "全部" else "All"
    private val GENRE_rexue = translateGenre("热血")
    private val GENRE_maoxian = translateGenre("冒险")
    private val GENRE_mohuan = translateGenre("魔幻")
    private val GENRE_shengui = translateGenre("神鬼")
    private val GENRE_gaoxiao = translateGenre("搞笑")
    private val GENRE_mengxi = translateGenre("萌系")
    private val GENRE_aiqing = translateGenre("爱情")
    private val GENRE_kehuan = translateGenre("科幻")
    private val GENRE_mofa = translateGenre("魔法")
    private val GENRE_gedou = translateGenre("格斗")
    private val GENRE_wuxia = translateGenre("武侠")
    private val GENRE_jizhan = translateGenre("机战")
    private val GENRE_zhanzheng = translateGenre("战争")
    private val GENRE_jingji = translateGenre("竞技")
    private val GENRE_tiyu = translateGenre("体育")
    private val GENRE_xiaoyuan = translateGenre("校园")
    private val GENRE_shenghuo = translateGenre("生活")
    private val GENRE_lizhi = translateGenre("励志")
    private val GENRE_lishi = translateGenre("历史")
    private val GENRE_weiniang = translateGenre("伪娘")
    private val GENRE_zhainan = translateGenre("宅男")
    private val GENRE_funv = translateGenre("腐女")
    private val GENRE_danmei = translateGenre("耽美")
    private val GENRE_baihe = translateGenre("百合")
    private val GENRE_hougong = translateGenre("后宫")
    private val GENRE_zhiyu = translateGenre("治愈")
    private val GENRE_meishi = translateGenre("美食")
    private val GENRE_tuili = translateGenre("推理")
    private val GENRE_xuanyi = translateGenre("悬疑")
    private val GENRE_kongbu = translateGenre("恐怖")
    private val GENRE_sige = translateGenre("四格")
    private val GENRE_zhichang = translateGenre("职场")
    private val GENRE_zhentan = translateGenre("侦探")
    private val GENRE_shehui = translateGenre("社会")
    private val GENRE_yinyue = translateGenre("音乐")
    private val GENRE_wudao = translateGenre("舞蹈")
    private val GENRE_zazhi = translateGenre("杂志")
    private val GENRE_heidao = translateGenre("黑道")

    private fun translateGenre(it: String): String {
        if (isChinese) return it
        return when (it) {
            "热血" -> "Passionate"
            "冒险" -> "Adventure"
            "魔幻" -> "Fantasy"
            "神鬼" -> "Gods and ghosts"
            "搞笑" -> "Funny"
            "萌系" -> "Cute"
            "爱情" -> "Love"
            "科幻" -> "Science fiction"
            "魔法" -> "Magic"
            "格斗" -> "Fighting"
            "武侠" -> "Martial arts"
            "机战" -> "Aircraft warfare"
            "战争" -> "War"
            "竞技" -> "Sports"
            "体育" -> "Physical education"
            "校园" -> "Campus"
            "生活" -> "Life"
            "励志" -> "Inspirational"
            "历史" -> "History"
            "伪娘" -> "Femboy"
            "宅男" -> "Otaku"
            "腐女" -> "Fujoshi"
            "耽美" -> "Boys love"
            "百合" -> "Lily"
            "后宫" -> "Harem"
            "治愈" -> "Cure"
            "美食" -> "Gourmet food"
            "推理" -> "Reasoning"
            "悬疑" -> "Suspense"
            "恐怖" -> "Fear"
            "四格" -> "4-panel strip"
            "职场" -> "Workplace"
            "侦探" -> "Detective"
            "社会" -> "Society"
            "音乐" -> "Music"
            "舞蹈" -> "Dance"
            "杂志" -> "Magazine"
            "黑道" -> "Underworld"
            else -> it
        }
    }

    private val BY_AUDIENCE = if (isChinese) "按受众" else "By audience"
    private val AUDIENCE_ALL = if (isChinese) "全部" else "All"
    private val AUDIENCE_shaonv = if (isChinese) "少女" else "Girl"
    private val AUDIENCE_shaonian = if (isChinese) "少年" else "Juvenile"
    private val AUDIENCE_qingnian = if (isChinese) "青年" else "Youth"
    private val AUDIENCE_ertong = if (isChinese) "儿童" else "Child"
    private val AUDIENCE_tongyong = if (isChinese) "通用" else "Universal"

    private val BY_YEAR = if (isChinese) "按年份" else "By year"
    private val YEAR_ALL = if (isChinese) "全部" else "All"
    private val YEAR_2020 = if (isChinese) "2020年" else "2020"
    private val YEAR_2019 = if (isChinese) "2019年" else "2019"
    private val YEAR_2018 = if (isChinese) "2018年" else "2018"
    private val YEAR_2017 = if (isChinese) "2017年" else "2017"
    private val YEAR_2016 = if (isChinese) "2016年" else "2016"
    private val YEAR_2015 = if (isChinese) "2015年" else "2015"
    private val YEAR_2014 = if (isChinese) "2014年" else "2014"
    private val YEAR_2013 = if (isChinese) "2013年" else "2013"
    private val YEAR_2012 = if (isChinese) "2012年" else "2012"
    private val YEAR_2011 = if (isChinese) "2011年" else "2011"
    private val YEAR_2010 = if (isChinese) "2010年" else "2010"
    private val YEAR_200x = if (isChinese) "00年代" else "20s"
    private val YEAR_199x = if (isChinese) "90年代" else "90s"
    private val YEAR_198x = if (isChinese) "80年代" else "80s"
    private val YEAR_197x = if (isChinese) "更早" else "Earlier"

    private val BY_FIRST_LETER = if (isChinese) "按字母" else "By first leter"
    private val FIRST_LETTER_ALL = if (isChinese) "全部" else "All"

    companion object {
        private const val SHOW_R18_PREF = "showR18Default"

        private const val SHOW_ZH_HANT_WEBSITE_PREF = "showZhHantWebsite"

        private const val USE_MIRROR_URL_PREF = "useMirrorWebsitePreference"

        private const val MAINSITE_RATELIMIT_PREF = "mainSiteRatelimitPreference"
        private const val MAINSITE_RATELIMIT_DEFAULT_VALUE = "10"

        private const val IMAGE_CDN_RATELIMIT_PREF = "imgCDNRatelimitPreference"
        private const val IMAGE_CDN_RATELIMIT_DEFAULT_VALUE = "4"

        private val ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()
        const val PREFIX_ID_SEARCH = "id:"
    }
}
