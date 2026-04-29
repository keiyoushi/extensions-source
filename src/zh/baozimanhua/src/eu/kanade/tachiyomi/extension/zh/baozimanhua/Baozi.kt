package eu.kanade.tachiyomi.extension.zh.baozimanhua

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.github.stevenyomi.baozibanner.BaoziBanner
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Baozi :
    HttpSource(),
    ConfigurableSource {

    override val id = 5724751873601868259

    override val name = "包子漫画"

    private val preferences: SharedPreferences = getPreferences()

    private val domain: String = run {
        val mirrors = MIRRORS
        val domain = preferences.getString(MIRROR_PREF, null) ?: return@run mirrors[0]
        if (domain in mirrors) return@run domain
        preferences.edit().remove(MIRROR_PREF).apply()
        mirrors[0]
    }

    override val baseUrl = "https://$domain"

    override val lang = "zh"

    override val supportsLatest = true

    private val bannerInterceptor = BaoziBanner(
        level = preferences.getString(BaoziBanner.PREF, DEFAULT_LEVEL)!!.toInt(),
    )

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addInterceptor(bannerInterceptor)
        .addNetworkInterceptor(MissingImageInterceptor)
        .addNetworkInterceptor(RedirectDomainInterceptor(domain))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "https://$domain/")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val fullListTitle = document.selectFirst(".section-title:containsOwn(章节目录), .section-title:containsOwn(章節目錄)")
        val chapterElements = if (fullListTitle == null) { // only latest chapters
            document.select(".comics-chapters")
        } else {
            // chapters are listed oldest to newest in the source
            fullListTitle.parent()?.select(".comics-chapters")?.reversed() ?: emptyList()
        }

        return chapterElements.map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")?.attr("abs:href").orEmpty())
                name = element.text()
            }
        }.apply {
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
                    this[0].date_upload = DATE_FORMAT.tryParse(date.removePrefix("(").removeSuffix(" 更新)"))
                }
            }
        }
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/classify?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.pure-g div a.comics-card__poster").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.attr("title").trim()
                thumbnail_url = element.selectFirst("> amp-img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, mangas.size == 36)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list/new", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.pure-g div a.comics-card__poster").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.attr("title").trim()
                thumbnail_url = element.selectFirst("> amp-img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.comics-detail__title")?.text().orEmpty()
            thumbnail_url = document.selectFirst("div.pure-g div > amp-img")?.attr("abs:src")
            author = document.selectFirst("h2.comics-detail__author")?.text().orEmpty()
            description = document.selectFirst("p.comics-detail__desc")?.text().orEmpty()
            status = when (document.selectFirst("div.tag-list > span.tag")?.text()) {
                "连载中", "連載中" -> SManga.ONGOING
                "已完结", "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val urls = mutableListOf<String>()

        var chapterUrl = baseUrl + chapter.url
        if (preferences.getBoolean(QUICK_PAGES_PREF, true)) {
            chapterUrl = quickPageUrl(chapterUrl)
        }

        var request = GET(chapterUrl, headers).newBuilder().build()
        while (true) {
            val document = client.newCall(request).execute().use { it.asJsoup() }
            urls.addAll(document.select(".comic-contain amp-img").map { it.attr("abs:src") })

            val url = document.selectFirst("#next-chapter")
                ?.takeIf {
                    val text = it.text()
                    text == "下一页" || text == "下一頁"
                }
                ?.attr("abs:href")
                ?: break

            request = GET(url, headers)
        }
        urls.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private fun quickPageUrl(url: String): String = baseUrl.toHttpUrl().newBuilder().apply {
        val chapUrl = url.toHttpUrl()
        addPathSegments("comic/chapter")
        chapUrl.queryParameter("comic_id")?.let { addPathSegment(it) }
        addPathSegment("${chapUrl.queryParameter("section_slot")}_${chapUrl.queryParameter("chapter_slot")}.html")
    }.build().toString()

    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!.replace(".baozicdn.com", ".baozimh.com")
        return GET(url, headers)
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(ID_SEARCH_PREFIX)) {
        val id = query.removePrefix(ID_SEARCH_PREFIX)
        client.newCall(searchMangaByIdRequest(id))
            .asObservableSuccess()
            .map { response -> searchMangaByIdParse(response, id) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/comic/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val sManga = mangaDetailsParse(response)
        sManga.url = "/comic/$id"
        return MangasPage(listOf(sManga), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotEmpty()) {
        val requestBaseUrl = baseUrl.replace(".dinnerku.com", ".baozimh.com")
        val url = requestBaseUrl.toHttpUrl().newBuilder()
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
        val mangas = document.select("div.pure-g div a.comics-card__poster").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.attr("title").trim()
                thumbnail_url = element.selectFirst("> amp-img")?.attr("abs:src")
            }
        }
        val isSearch = response.request.url.encodedPath.contains("search")
        return MangasPage(mangas, !isSearch && mangas.size == 36)
    }

    override fun getFilterList() = getFilters()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            val mirrors = MIRRORS

            key = MIRROR_PREF
            title = "使用镜像网址"
            entries = mirrors
            entryValues = mirrors
            summary = "已选择：%s\n" +
                "重启生效，切换简繁体后需要迁移才能刷新漫画标题。"
            setDefaultValue(mirrors[0])
        }.let { screen.addPreference(it) }

        ListPreference(screen.context).apply {
            key = BaoziBanner.PREF
            title = BaoziBanner.PREF_TITLE
            summary = BaoziBanner.PREF_SUMMARY
            entries = BaoziBanner.PREF_ENTRIES
            entryValues = BaoziBanner.PREF_VALUES
            setDefaultValue(DEFAULT_LEVEL)
            setOnPreferenceChangeListener { _, newValue ->
                bannerInterceptor.level = (newValue as String).toInt()
                true
            }
        }.let { screen.addPreference(it) }

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
        }.let { screen.addPreference(it) }

        CheckBoxPreference(screen.context).apply {
            key = QUICK_PAGES_PREF
            title = "Quick Pages/快速页面"
            summary = "跳过页面上的重定向。五月休息。(对不起，必须使用翻译器)"
            setDefaultValue(true)
        }.let { screen.addPreference(it) }
    }

    companion object {
        const val ID_SEARCH_PREFIX = "id:"

        private const val MIRROR_PREF = "MIRROR"
        private val MIRRORS get() = arrayOf(
            "cn.baozimh.com",
            "tw.baozimh.com",
            "www.baozimh.com",
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

        private const val DEFAULT_LEVEL = BaoziBanner.NORMAL.toString()

        private const val CHAPTER_ORDER_PREF = "CHAPTER_ORDER"
        private const val CHAPTER_ORDER_DISABLED = "0"
        private const val CHAPTER_ORDER_ENABLED = "1"
        private const val CHAPTER_ORDER_AGGRESSIVE = "2"

        private val DATE_FORMAT by lazy { SimpleDateFormat("yyyy年MM月dd日", Locale.ENGLISH) }

        private const val QUICK_PAGES_PREF = "QUICK_PAGES"
    }
}
