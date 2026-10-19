package eu.kanade.tachiyomi.extension.zh.baozimanhua

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Baozi :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val isAppMirror: Boolean
        get() = baseUrl.toHttpUrl().host.startsWith("app")

    private val siteBaseUrl: String
        get() = if (isAppMirror) "$baseUrl/baozimhapp" else baseUrl

    private val bannerInterceptor = BaoziBannerInterceptor(
        level = preferences.getString(BaoziBannerInterceptor.PREF, DEFAULT_LEVEL)!!.toInt(),
    )

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(bannerInterceptor)
        addNetworkInterceptor(MissingImageInterceptor)
        rateLimit(2)
    }

    private fun normalizeRelativeUrl(url: String): String {
        val resolvedUrl = baseUrl.toHttpUrl().resolve(url) ?: return url
        return buildString {
            append(resolvedUrl.encodedPath.removePrefix("/baozimhapp"))
            resolvedUrl.encodedQuery?.let { append('?').append(it) }
        }
    }

    private fun chapterListParse(document: Document): List<SChapter> {
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
                    this[0].date_upload = DATE_FORMAT.tryParseDate(
                        date.removePrefix("(").removeSuffix(" 更新)"),
                        ZoneOffset.UTC,
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

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$siteBaseUrl/classify?page=$page")
        val document = response.asJsoup()

        val mangas = parseMangaCards(document)
            .filter { it.title.isNotEmpty() && !it.title.contains("{{") }

        return MangasPage(mangas, mangas.size >= 36)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = if (isAppMirror) {
        getPopularManga(page)
    } else {
        val document = client.get("$baseUrl/list/new").asJsoup()
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

        return elements.mapNotNull { mangaFromElement(it) }
    }

    private fun mangaFromElement(element: Element): SManga? {
        // element may be the wrapper div.comics-card OR the <a.comics-card__poster> itself
        val poster = element.selectFirst(".comics-card__poster")
            ?: element.takeIf { it.tagName() == "a" && it.hasClass("comics-card__poster") }

        val mangaUrl = if (poster?.attr("href").isNullOrBlank()) {
            // app sometimes only has onclick
            val onclick = poster?.attr("onclick").orEmpty()
            val slug = onclick.substringAfter("'comic', '").substringBefore("'")
            normalizeRelativeUrl("/comic/$slug")
        } else {
            normalizeRelativeUrl(poster!!.attr("href"))
        }

        val titleElement = element.selectFirst(".comics-card__title")
        val mangaTitle = titleElement?.text()
            ?.takeIf(String::isNotBlank)
            ?: poster?.attr("title")?.takeIf(String::isNotBlank)
            ?: return null

        return SManga.create().apply {
            url = mangaUrl
            title = mangaTitle.trim()

            val img = element.selectFirst("img, amp-img")
            thumbnail_url = img?.absUrl("data-src")?.ifEmpty { img.absUrl("src") }
        }
    }

    // --- Details ---

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.comics-detail__title")?.text()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: error("Missing manga title")
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

    // --- Pages ---

    // Web mirrors now serve an app-download wall with placeholder images instead of the chapter;
    // only the app reader still has the real pages
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(appChapterUrl(chapter.url)).asJsoup()
        .select(
            ".comic-contain amp-img, .comic-contain img, .comic-article img, " +
                ".chapter-img amp-img, .chapter-img img, " +
                ".comic-page amp-img, .comic-page img, " +
                "[class*=chapter] amp-img, [class*=chapter] img, " +
                "[class*=comic] amp-img, [class*=comic] img",
        )
        .mapNotNull { it.absUrl("data-src").ifEmpty { it.absUrl("src") }.ifEmpty { null } }
        .filter { !isExcludedImage(it) }
        .distinct()
        .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }

    private fun appChapterUrl(chapterUrl: String): String {
        val url = baseUrl.toHttpUrl().resolve(chapterUrl)!!
        val comicId = url.queryParameter("comic_id")
        val (id, slot) = if (comicId != null) {
            comicId to "${url.queryParameter("section_slot")}_${url.queryParameter("chapter_slot")}.html"
        } else {
            val segments = url.pathSegments
            val index = segments.indexOf("chapter")
            segments[index + 1] to segments[index + 2]
        }
        val appBaseUrl = if (isAppMirror) baseUrl else APP_BASE_URL
        return "$appBaseUrl/baozimhapp/comic/chapter/$id/$slot"
    }

    private fun isExcludedImage(url: String): Boolean = url.contains("/cover/") ||
        url.contains("logo") ||
        url.contains("loading.gif") ||
        url.contains("404.png") ||
        url.contains("favicon") ||
        url.contains("/img/") ||
        url.contains("banner") ||
        url.contains("recommend")

    // baozicdn.com geo-redirects to regional nodes that are sometimes unreachable, baozimh.com serves directly
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!.replace(".baozicdn.com", ".baozimh.com"), headers).newBuilder()
        .tag(BaoziBannerInterceptor.ReaderPageImageTag::class.java, BaoziBannerInterceptor.ReaderPageImageTag)
        .build()

    // --- Search ---

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val requestUrl = if (query.isNotEmpty()) {
            siteBaseUrl.toHttpUrl().newBuilder()
                .addEncodedPathSegment("search")
                .addQueryParameter("q", query)
                .toString()
        } else {
            val parts = filters.filterIsInstance<UriPartFilter>().joinToString("&") { it.toUriPart() }
            "$siteBaseUrl/classify?page=$page&$parts"
        }

        val response = client.get(requestUrl)
        val document = response.asJsoup()

        val mangas = parseMangaCards(document)
            .filter { it.title.isNotEmpty() && !it.title.contains("{{") }

        val isSearch = response.request.url.encodedPath.contains("search")
        return MangasPage(mangas, !isSearch && mangas.size >= 36)
    }

    override suspend fun getMangaByUrl(url: okhttp3.HttpUrl): SManga? {
        if (url.host !in MIRRORS) return null

        val comicIndex = url.pathSegments.indexOf("comic")
        if (comicIndex == -1) return null

        val id = if (url.pathSegments.getOrNull(comicIndex + 1) == "chapter") {
            url.pathSegments.getOrNull(comicIndex + 2)
        } else {
            url.pathSegments.getOrNull(comicIndex + 1)
        } ?: return null

        return mangaDetailsParse(client.get("$siteBaseUrl/comic/$id").asJsoup()).apply {
            this.url = normalizeRelativeUrl("/comic/$id")
        }
    }

    override fun getMangaUrl(manga: SManga): String = siteBaseUrl + manga.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document).apply { url = manga.url },
            chapters = chapterListParse(document),
        )
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

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

        private const val APP_BASE_URL = "https://appcn.baozimh.com"

        private const val DEFAULT_LEVEL = BaoziBannerInterceptor.NORMAL.toString()

        private const val CHAPTER_ORDER_PREF = "CHAPTER_ORDER"
        private const val CHAPTER_ORDER_DISABLED = "0"
        private const val CHAPTER_ORDER_ENABLED = "1"
        private const val CHAPTER_ORDER_AGGRESSIVE = "2"

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.ENGLISH)
    }
}
