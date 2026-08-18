package eu.kanade.tachiyomi.extension.zh.baozimanhua

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Baozi :
    HttpSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences = getPreferences()

    override val supportsLatest = true

    private val basePath: String
        get() = baseUrl.toHttpUrl().encodedPath.trimEnd('/')

    private val bannerInterceptor = BaoziBannerInterceptor(
        level = preferences.getString(BaoziBannerInterceptor.PREF, DEFAULT_LEVEL)!!.toInt(),
    )

    override val client by lazy {
        network.client.newBuilder()
            .addInterceptor(bannerInterceptor)
            .addNetworkInterceptor(MissingImageInterceptor)
            .addNetworkInterceptor(RedirectDomainInterceptor { baseUrl.toHttpUrl().host })
            .rateLimit(2)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun normalizeRelativeUrl(url: String): String {
        val resolvedUrl = baseUrl.toHttpUrl().resolve(url) ?: return url
        return buildString {
            append(resolvedUrl.encodedPath.removePrefix(basePath))
            resolvedUrl.encodedQuery?.let { append('?').append(it) }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val fullListTitle = document.selectFirst(".section-title:containsOwn(章节目录), .section-title:containsOwn(章節目錄)")

        val chapterElements = if (fullListTitle == null) {
            document.select(".comics-chapters")
        } else {
            fullListTitle.parent()?.select(".comics-chapters")?.reversed() ?: emptyList()
        }

        return chapterElements.map { chapterFromElement(it) }.apply {
            val chapterOrderPref = preferences.getString(CHAPTER_ORDER_PREF, CHAPTER_ORDER_DISABLED)
            if (chapterOrderPref != CHAPTER_ORDER_DISABLED) {
                val isAggressive = chapterOrderPref == CHAPTER_ORDER_AGGRESSIVE
                forEach {
                    if (isAggressive || it.name.any(Char::isDigit)) {
                        it.url = "${it.url}#${it.name}"
                    }
                }
            }

            if (isNotEmpty()) {
                val date = document.selectFirst("em")?.text().orEmpty()
                if (date.contains('年')) {
                    this[0].date_upload = DATE_FORMAT.tryParse(
                        date.removePrefix("(").removeSuffix(" 更新)"),
                    )
                }
            }
        }
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val linkElement = element.selectFirst("a")
        val href = linkElement?.attr("href")?.trim().orEmpty()

        url = if (href.isNotEmpty()) {
            // Web version
            normalizeRelativeUrl(href)
        } else {
            // App version: onclick="send_app_msg('call_page', ['chapter', 'slug', section, chapter])"
            val onclick = element.selectFirst("[onclick]")?.attr("onclick").orEmpty()
            val match = Regex(
                """send_app_msg\('call_page',\s*\['chapter',\s*'([^']+)',\s*(\d+),\s*(\d+)\]\)""",
            ).find(onclick)

            if (match != null) {
                val slug = match.groupValues[1]
                val section = match.groupValues[2]
                val chapter = match.groupValues[3]
                normalizeRelativeUrl("/comic/chapter/$slug/${section}_$chapter.html")
            } else {
                ""
            }
        }

        name = element.text()
    }

    // --- Listings (popular/latest/search results) ---

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/classify?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        // Use manual parse to ensure baseUri is correct, and filter template ghost cards
        val body = response.body.string()
        val document = Jsoup.parse(body, response.request.url.toString())

        val mangas = parseMangaCards(document)
            .filter { it.title.isNotEmpty() && !it.title.contains("{{") }

        return MangasPage(mangas, mangas.size >= 36)
    }

    override fun latestUpdatesRequest(page: Int): Request = if (basePath.isNotEmpty()) {
        GET("$baseUrl/classify?page=$page", headers)
    } else {
        GET("$baseUrl/list/new", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = if (basePath.isNotEmpty()) {
        popularMangaParse(response)
    } else {
        val document = response.asJsoup()
        val mangas = parseMangaCards(document)
            .filter { it.title.isNotEmpty() && !it.title.contains("{{") }

        // /list/new typically doesn't paginate like classify does
        MangasPage(mangas, false)
    }

    private fun parseMangaCards(document: Document): List<SManga> {
        val cards = document.select("div.comics-card")
        val elements = if (cards.isNotEmpty()) {
            cards
        } else {
            // fallback for older layout / other pages
            document.select("div.pure-g div a.comics-card__poster")
        }

        return elements.map { mangaFromElement(it) }
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        // element may be the wrapper div.comics-card OR the <a.comics-card__poster> itself
        val poster = element.selectFirst(".comics-card__poster")
            ?: element.takeIf { it.tagName() == "a" && it.hasClass("comics-card__poster") }

        val href = poster?.attr("href").orEmpty()
        if (href.isNotBlank()) {
            url = normalizeRelativeUrl(href)
        } else {
            // app sometimes only has onclick
            val onclick = poster?.attr("onclick").orEmpty()
            val slug = onclick.substringAfter("'comic', '").substringBefore("'")
            url = normalizeRelativeUrl("/comic/$slug")
        }

        val titleElement = element.selectFirst(".comics-card__title")
        title = titleElement?.text()?.trim()
            ?: poster?.attr("title")?.trim()
            ?: ""

        val img = element.selectFirst("img, amp-img")
        thumbnail_url = img?.absUrl("data-src")?.ifEmpty { img.absUrl("src") }
    }

    // --- Details ---

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.comics-detail__title")?.text().orEmpty()
            thumbnail_url = document.selectFirst("meta[name=og:image]")?.attr("content")
                ?: document.selectFirst("div.pure-g div > amp-img, div.pure-g div > img")?.absUrl("src")
            author = document.selectFirst("h2.comics-detail__author")?.text().orEmpty()
            description = document.selectFirst("p.comics-detail__desc")?.text().orEmpty()
            status = when (document.selectFirst("div.tag-list > span.tag")?.text()) {
                "连载中", "連載中" -> SManga.ONGOING
                "已完结", "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // --- Pages (with REMOVE_DUPLICATE_IMAGES merged) ---

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val urls = mutableListOf<String>()
        val isQuickPage = preferences.getBoolean(QUICK_PAGES_PREF, true)

        var chapterUrl = if (chapter.url.startsWith("http")) {
            chapter.url
        } else {
            baseUrl + chapter.url
        }

        // Only convert/quickpage on app mirrors (to avoid breaking web mirrors)
        if (basePath.isNotEmpty() && isQuickPage) {
            chapterUrl = quickPageUrl(chapterUrl)
        }

        val isAlreadyAppFormat = try {
            val u = chapterUrl.toHttpUrl()
            u.querySize == 0 && u.encodedPath.contains(Regex("""/\d+_\d+\.html$"""))
        } catch (_: Throwable) {
            false
        }

        val shouldSkipPagination = basePath.isNotEmpty() && isQuickPage && isAlreadyAppFormat

        var request = GET(chapterUrl, headers)
        var lastImageId: Int? = null

        while (true) {
            val (document, responseUrl) = client.newCall(request).execute().use { resp ->
                val responseUrlInner = resp.request.url.toString()
                val bodyString = resp.body.string()
                Jsoup.parse(bodyString, responseUrlInner) to responseUrlInner
            }

            val pageImages = document.select(
                ".comic-contain amp-img, .comic-contain img, .comic-article img, " +
                    ".chapter-img amp-img, .chapter-img img, " +
                    ".comic-page amp-img, .comic-page img, " +
                    "[class*=chapter] amp-img, [class*=chapter] img, " +
                    "[class*=comic] amp-img, [class*=comic] img",
            )
                .mapNotNull {
                    val src = it.absUrl("data-src").ifEmpty { it.absUrl("src") }
                    src.ifEmpty { null }
                }
                .filter { !isExcludedImage(it) }
                .distinct()

            val filteredImages = if (
                preferences.getBoolean(REMOVE_DUPLICATE_IMAGES_PREF, false) && lastImageId != null
            ) {
                filterImagesByMinId(pageImages, lastImageId!! + 1)
            } else {
                pageImages
            }

            urls.addAll(filteredImages)
            lastImageId = filteredImages.maxOfOrNull { extractImageId(it) } ?: lastImageId

            if (shouldSkipPagination) break

            val nextButton = document.selectFirst("#next-chapter, .next-page, a:contains(下一页), a:contains(下一頁)")
            val nextUrl = nextButton?.attr("abs:href")

            if (nextUrl != null &&
                nextUrl != responseUrl &&
                (nextButton.text().contains("下一页") || nextButton.text().contains("下一頁"))
            ) {
                request = GET(nextUrl, headers)
            } else {
                break
            }
        }

        urls.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private fun quickPageUrl(url: String): String {
        val httpUrl = url.toHttpUrl()
        val isAppFormat = httpUrl.querySize == 0
        return if (isAppFormat) {
            url
        } else {
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegments("comic/chapter")
                httpUrl.queryParameter("comic_id")?.let { addPathSegment(it) }
                val section = httpUrl.queryParameter("section_slot")
                val chapter = httpUrl.queryParameter("chapter_slot")
                addPathSegment("${section}_$chapter.html")
            }.build().toString()
        }
    }

    private fun isExcludedImage(url: String): Boolean = url.contains("/cover/") ||
        url.contains("logo") ||
        url.contains("loading.gif") ||
        url.contains("404.png") ||
        url.contains("favicon") ||
        url.contains("/img/") ||
        url.contains("banner") ||
        url.contains("recommend")

    private fun extractImageId(imageUrl: String): Int {
        val clean = imageUrl.substringBefore('?')
        val regex = """/(\d+)\.(jpg|jpeg|png|webp|gif)$""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(clean)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun filterImagesByMinId(imageUrls: List<String>, minId: Int): List<String> = imageUrls.filter { extractImageId(it) >= minId }

    override fun imageRequest(page: Page): Request {
        val original = page.imageUrl!!
            .replace(".baozicdn.com", ".baozimh.com")

        return GET(original, headers).newBuilder()
            .tag(BaoziBannerInterceptor.ReaderPageImageTag::class.java, BaoziBannerInterceptor.ReaderPageImageTag)
            .tag(RedirectDomainInterceptor.Tag::class.java, RedirectDomainInterceptor.Tag())
            .build()
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // --- Search ---

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host !in MIRRORS) throw Exception("Unsupported url")

            val seg = url.pathSegments
            val comicIndex = seg.indexOf("comic")
            if (comicIndex == -1) throw Exception("Unsupported url")

            val id = if (seg.getOrNull(comicIndex + 1) == "chapter") {
                seg.getOrNull(comicIndex + 2)
            } else {
                seg.getOrNull(comicIndex + 1)
            } ?: throw Exception("Unsupported url")

            return fetchSearchManga(page, "$ID_SEARCH_PREFIX$id", filters)
        }

        return if (query.startsWith(ID_SEARCH_PREFIX)) {
            val id = query.removePrefix(ID_SEARCH_PREFIX)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/comic/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val sManga = mangaDetailsParse(response)
        sManga.url = normalizeRelativeUrl("/comic/$id")
        return MangasPage(listOf(sManga), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotEmpty()) {
        val searchDomain = baseUrl.toHttpUrl().host.replace(".dinnerku.com", ".baozimh.com")
        val searchPath = if (searchDomain.startsWith("app")) "/baozimhapp" else ""
        val url = "https://$searchDomain$searchPath".toHttpUrl().newBuilder()
            .addEncodedPathSegment("search")
            .addQueryParameter("q", query)
            .toString()
        GET(url, headers)
    } else {
        val parts = filters.filterIsInstance<UriPartFilter>().joinToString("&") { it.toUriPart() }
        GET("$baseUrl/classify?page=$page&$parts", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = parseMangaCards(document)
            .filter { it.title.isNotEmpty() && !it.title.contains("{{") }

        val isSearch = response.request.url.encodedPath.contains("search")
        return MangasPage(mangas, !isSearch && mangas.size >= 36)
    }

    override fun getFilterList() = getFilters()

    // --- Preferences ---

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = BaoziBannerInterceptor.PREF
            title = BaoziBannerInterceptor.PREF_TITLE
            summary = BaoziBannerInterceptor.PREF_SUMMARY
            entries = BaoziBannerInterceptor.PREF_ENTRIES
            entryValues = BaoziBannerInterceptor.PREF_VALUES
            setDefaultValue(DEFAULT_LEVEL)
            setOnPreferenceChangeListener { _, newValue ->
                bannerInterceptor.level = (newValue as String).toInt()
                true
            }
        }.let(screen::addPreference)

        CheckBoxPreference(screen.context).apply {
            key = REMOVE_DUPLICATE_IMAGES_PREF
            title = "移除新頁面重複圖片"
            summary = "包子漫畫分頁會顯示上一頁最後幾張圖片，開啓功能可以移除重複的圖片"
            setDefaultValue(false)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = CHAPTER_ORDER_PREF
            title = "修复章节顺序错误导致的错标已读"
            summary = "已选择：%s\n" +
                "部分作品的章节顺序错误，最新章节总是显示为一个旧章节，导致检查更新时新章节被错标为已读。" +
                "开启后，将会正确判断新章节和已读情况，但是错误的章节顺序不会改变。" +
                "警告：修改此设置后第一次刷新可能会导致已读状态出现错乱，请谨慎使用。"
            entries = arrayOf("关闭", "开启 (对有标号的章节有效)", "强力模式 (对所有章节有效)")
            entryValues = arrayOf(CHAPTER_ORDER_DISABLED, CHAPTER_ORDER_ENABLED, CHAPTER_ORDER_AGGRESSIVE)
            setDefaultValue(CHAPTER_ORDER_DISABLED)
        }.let(screen::addPreference)

        CheckBoxPreference(screen.context).apply {
            key = QUICK_PAGES_PREF
            title = "Quick Pages/快速页面"
            summary = "跳过页面上的重定向。五月休息。(对不起，必须使用翻译器)"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    companion object {
        const val ID_SEARCH_PREFIX = "id:"

        private val MIRRORS get() = arrayOf(
            "cn.baozimh.com",
            "tw.baozimh.com",
            "www.baozimh.com",
            "appcn.baozimh.com",
            "appgb.baozimh.com",
            "cn.webmota.com",
            "tw.webmota.com",
            "www.webmota.com",
            "cn.kukuc.co",
            "tw.kukuc.co",
            "www.kukuc.co",
            "cn.twmanga.com",
            "tw.twmanga.com",
            "www.twmanga.com",
            "cn.dinnerku.com",
            "tw.dinnerku.com",
            "www.dinnerku.com",
        )

        private const val DEFAULT_LEVEL = BaoziBannerInterceptor.NORMAL.toString()

        private const val CHAPTER_ORDER_PREF = "CHAPTER_ORDER"
        private const val CHAPTER_ORDER_DISABLED = "0"
        private const val CHAPTER_ORDER_ENABLED = "1"
        private const val CHAPTER_ORDER_AGGRESSIVE = "2"

        private val DATE_FORMAT by lazy { SimpleDateFormat("yyyy年MM月dd日", Locale.ENGLISH) }

        private const val QUICK_PAGES_PREF = "QUICK_PAGES"
        private const val REMOVE_DUPLICATE_IMAGES_PREF = "REMOVE_DUPLICATE_IMAGES"
    }
}
