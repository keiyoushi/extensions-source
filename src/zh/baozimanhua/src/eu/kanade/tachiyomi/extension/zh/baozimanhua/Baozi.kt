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
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
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

    private val appBaseUrl: String
        get() = if (isAppMirror) baseUrl else APP_BASE_URL

    private val bannerInterceptor = BaoziBannerInterceptor(
        level = preferences.getString(BaoziBannerInterceptor.PREF, DEFAULT_LEVEL)!!.toInt(),
    )

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(bannerInterceptor)
        addInterceptor(GatekeeperInterceptor)
        addNetworkInterceptor(MissingImageInterceptor)
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = siteBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("classify")
            .addQueryParameter("page", page.toString())
            .build()
        val mangas = parseMangaCards(client.get(url).asJsoup())
        return MangasPage(mangas, mangas.size >= 36)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = if (isAppMirror) {
        getPopularManga(page)
    } else {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("list/new").build()
        MangasPage(parseMangaCards(client.get(url).asJsoup()), false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = siteBaseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegment("search")
                addQueryParameter("q", query)
            } else {
                addPathSegment("classify")
                addQueryParameter("page", page.toString())
                filters.filterIsInstance<UriPartFilter>().forEach { addQueryParameter(it.param, it.value) }
            }
        }.build()

        val mangas = parseMangaCards(client.get(url).asJsoup())
        return MangasPage(mangas, query.isEmpty() && mangas.size >= 36)
    }

    private fun parseMangaCards(document: Document): List<SManga> = document.select("div.comics-card")
        .mapNotNull(::mangaFromElement)

    private fun mangaFromElement(element: Element): SManga? {
        val title = element.selectFirst(".comics-card__title")?.text()
            // Card template the site fills in client-side
            ?.takeUnless { it.isEmpty() || it.contains("{{") }
            ?: return null
        val poster = element.selectFirst(".comics-card__poster") ?: return null
        val href = poster.attr("href").takeIf(String::isNotEmpty)
            // App mirror: onclick="send_app_msg('call_page', ['comic', 'slug'])"
            ?: COMIC_ONCLICK_REGEX.find(poster.attr("onclick"))?.let { "/comic/" + it.groupValues[1] }
            ?: return null

        return SManga.create().apply {
            url = normalizeRelativeUrl(href)
            this.title = title
            val img = element.selectFirst("amp-img, img")
            thumbnail_url = img?.absUrl("data-src")?.ifEmpty { img.absUrl("src") }
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host !in MIRRORS) return null

        val comicIndex = url.pathSegments.indexOf("comic")
        if (comicIndex == -1) return null

        val id = if (url.pathSegments.getOrNull(comicIndex + 1) == "chapter") {
            url.pathSegments.getOrNull(comicIndex + 2)
        } else {
            url.pathSegments.getOrNull(comicIndex + 1)
        } ?: return null

        val manga = SManga.create().apply { this.url = "/comic/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
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

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.comics-detail__title")!!.text()
        thumbnail_url = document.selectFirst("meta[name=og:image]")?.attr("content")
        author = document.selectFirst("h2.comics-detail__author")?.textOrNull()?.split(',')?.joinToString()
        description = document.selectFirst("p.comics-detail__desc")?.textOrNull()
        // Status first, then region and genres
        val tags = document.select("div.tag-list > span.tag").map { it.text() }
        status = when (tags.firstOrNull()) {
            "连载中", "連載中" -> SManga.ONGOING
            "已完结", "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = tags.drop(1).joinToString().ifEmpty { null }
    }

    private fun chapterListParse(document: Document): List<SChapter> {
        // Series without a full chapter list only have the latest chapters block
        val chapterElements = document.select("#chapter-items .comics-chapters, #chapters_other_list .comics-chapters")
            .reversed()
            .ifEmpty { document.select(".comics-chapters") }

        return chapterElements.mapNotNull(::chapterFromElement).apply {
            val chapterOrderPref = preferences.getString(CHAPTER_ORDER_PREF, CHAPTER_ORDER_DISABLED)
            if (chapterOrderPref != CHAPTER_ORDER_DISABLED) {
                val isAggressive = chapterOrderPref == CHAPTER_ORDER_AGGRESSIVE
                forEach {
                    if (isAggressive || it.name.any(Char::isDigit)) {
                        it.url = "${it.url}#${it.name}"
                    }
                }
            }

            val date = document.selectFirst("em")?.text()
            if (date != null && date.contains('年')) {
                firstOrNull()?.date_upload = DATE_FORMAT.tryParseDate(
                    date.removePrefix("(").removeSuffix(" 更新)"),
                    ZoneOffset.UTC,
                )
            }
        }
    }

    private fun chapterFromElement(element: Element): SChapter? {
        val href = element.selectFirst("a[href]")?.attr("href")
        val url = if (href != null) {
            normalizeRelativeUrl(href)
        } else {
            // App mirror: onclick="send_app_msg('call_page', ['chapter', 'slug', section, chapter])"
            val match = CHAPTER_ONCLICK_REGEX.find(element.selectFirst("[onclick]")?.attr("onclick") ?: return null)
                ?: return null
            val (slug, section, chapter) = match.destructured
            normalizeRelativeUrl("/comic/chapter/$slug/${section}_$chapter.html")
        }

        return SChapter.create().apply {
            this.url = url
            name = element.text()
        }
    }

    // Web mirrors now serve an app-download wall with placeholder images instead of the chapter;
    // only the app reader still has the real pages
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(appChapterUrl(chapter.url)).asJsoup()
        .select(".comic-contain img[data-src]")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("data-src")) }

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
        return appBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("baozimhapp/comic/chapter")
            .addPathSegment(id)
            .addPathSegment(slot)
            .toString()
    }

    // baozicdn.com geo-redirects to regional nodes that are sometimes unreachable, baozimh.com serves directly
    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!.toHttpUrl()
        val host = url.host.replace(".baozicdn.com", ".baozimh.com")
        return GET(url.newBuilder().host(host).build(), headers).newBuilder()
            .tag(BaoziBannerInterceptor.ReaderPageImageTag::class.java, BaoziBannerInterceptor.ReaderPageImageTag)
            .build()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(appBaseUrl.toHttpUrl().newBuilder().addPathSegments("baozimhapp/classify").build()).asJsoup()
        .select(".classify-nav")
        .map { nav ->
            val options = nav.select("[onclick]").map { FILTER_ONCLICK_REGEX.find(it.attr("onclick"))!! to it.text() }
            FilterGroup(
                param = options.first().first.groupValues[1],
                options = options.map { (match, name) -> FilterOption(name, match.groupValues[2]) },
            )
        }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<FilterGroup>>())

    private fun normalizeRelativeUrl(url: String): String {
        val resolvedUrl = baseUrl.toHttpUrl().resolve(url) ?: return url
        return buildString {
            append(resolvedUrl.encodedPath.removePrefix("/baozimhapp"))
            resolvedUrl.encodedQuery?.let { append('?').append(it) }
        }
    }

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
        private val MIRRORS = setOf(
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

        private val COMIC_ONCLICK_REGEX = Regex("""\['comic',\s*'([^']+)'""")
        private val CHAPTER_ONCLICK_REGEX = Regex("""send_app_msg\('call_page',\s*\['chapter',\s*'([^']+)',\s*(\d+),\s*(\d+)\]\)""")
        private val FILTER_ONCLICK_REGEX = Regex("""handle_change_filters\(this, '(\w+)', '([^']*)'\)""")

        private const val DEFAULT_LEVEL = BaoziBannerInterceptor.NORMAL.toString()

        private const val CHAPTER_ORDER_PREF = "CHAPTER_ORDER"
        private const val CHAPTER_ORDER_DISABLED = "0"
        private const val CHAPTER_ORDER_ENABLED = "1"
        private const val CHAPTER_ORDER_AGGRESSIVE = "2"

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.ROOT)
    }
}
