package eu.kanade.tachiyomi.extension.ja.linemanga

import android.content.SharedPreferences
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone

class LineManga :
    HttpSource(),
    ConfigurableSource {
    override val name = "Line Manga"
    override val baseUrl = "https://manga.line.me"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val timestamp: String
        get() = System.currentTimeMillis().toString()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 412) {
                throw IOException("This service can only be used from Japan.")
            }
            if (response.code == 404 && request.url.encodedPath.contains("book/viewer")) {
                throw IOException("Log in via WebView and rent or purchase this chapter via web or their LINE Manga app.")
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        // .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
        // Requires either desktop UA or this header with random string because of "Your request may be JSON hijacking" error.
        .add("X-Requested-With", "XMLHttpRequest")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/periodic/gender_ranking".toHttpUrl().newBuilder()
            .addQueryParameter("gender", "0")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_", timestamp)
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<EntryResponse>().result
        val mangas = result.items.filter { it.isLightNovel != true }.map { it.toSManga() }
        val hasNextPage = result.pager?.hasNext == true
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val calendar = Calendar.getInstance(jst)
        val weekday = calendar.get(Calendar.DAY_OF_WEEK)
        val url = "$apiUrl/daily_list".toHttpUrl().newBuilder()
            .addQueryParameter("week_day", weekday.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_", timestamp)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<Result>()
        val mangas = result.items.filter { it.isLightNovel != true }.map { it.toSManga() }
        val hasNextPage = result.pager?.hasNext == true
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search_product/list".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("_", timestamp)
                .build()
            return GET(url, headers)
        }

        val filters = filters.firstInstance<CategoryFilter>()
        val url = "$apiUrl/${filters.type}".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_", timestamp)
            .apply {
                if (filters.type == "daily_list") {
                    addQueryParameter("week_day", filters.value)
                }
                if (filters.type.contains("gender_ranking")) {
                    addQueryParameter("gender", filters.value)
                }
                if (filters.type == "genre_list") {
                    addQueryParameter("genre_id", filters.value)
                }
            }
        return GET(url.build(), headers).newBuilder().tag(filters.type).build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val tag = response.request.tag()
        return if (tag == "daily_list" || tag == "genre_list") latestUpdatesParse(response) else popularMangaParse(response)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // E -> fully bundled episodes/volumes
        if (manga.url.startsWith("E")) {
            val url = "$apiUrl/book/product_list".toHttpUrl().newBuilder()
                .addQueryParameter("product_id", manga.url)
                .build()
            return GET(url, headers)
        }

        // S or Z -> episodes in parts
        val url = "$apiUrl/book/product_list".toHttpUrl().newBuilder()
            .addQueryParameter("need_read_info", "1")
            .addQueryParameter("rows", "1000")
            .addQueryParameter("is_periodic", "1")
            .addQueryParameter("product_id", manga.url)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<EntryDetails>().result.product.toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/product/periodic?id=${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val id = response.request.url.queryParameter("product_id")!!
        val chapters = response.parseAs<EntryDetails>().result.rows.filter { !hideLocked || !it.isLocked }.map { it.toSChapter() }
        return if (id.startsWith("E")) chapters else chapters.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/book/viewer?id=${chapter.url}", headers)

    // TODO: Check entries for maybe Publus?:  mediado_token: '', mediado_contents_url: '', mediado_contents_file: 'configuration_pack.json',
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(var OPTION)")!!.data()
        val isPortal = IS_PORTAL.find(script)?.groupValues?.get(1) == "true"

        return if (isPortal) parsePortalPages(script) else parseImgPages(script)
    }

    private fun parseImgPages(script: String): List<Page> = IMG_PAGES
        .findAll(script)
        .sortedBy { it.groupValues[1].toInt() }
        .mapIndexed { i, page ->
            Page(i, imageUrl = page.groupValues[2])
        }
        .filter { !it.imageUrl!!.contains("inline_ads_banner") }
        .toList()

    private fun parsePortalPages(script: String): List<Page> {
        val pageIndices = INDICES
            .findAll(script)
            .map { it.groupValues[1].toInt() }
            .toSortedSet()

        if (pageIndices.isEmpty()) return emptyList()

        val urlMap = URL_MAP
            .findAll(script)
            .associate { it.groupValues[1].toInt() to it.groupValues[2] }

        val hcMap = HC_MAP
            .findAll(script)
            .associate { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

        val bwdMap = BWD_MAP
            .findAll(script)
            .associate { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

        // m entries: portal_pages[N].metadata.m[M] = 'base35value';
        val mMap = mutableMapOf<Int, MutableMap<Int, String>>()
        M_MAP
            .findAll(script)
            .forEach { match ->
                val pageIdx = match.groupValues[1].toInt()
                val mIdx = match.groupValues[2].toInt()
                val value = match.groupValues[3]
                mMap.getOrPut(pageIdx) { mutableMapOf() }[mIdx] = value
            }

        return pageIndices.mapIndexed { i, page ->
            val url = urlMap[page] ?: throw Exception("Missing url for portal_pages[$page]")
            val mEntries = mMap[page]

            val imgUrl = if (!mEntries.isNullOrEmpty()) {
                val hc = hcMap[page] ?: throw Exception("Missing hc for portal_pages[$page]")
                val bwd = bwdMap[page] ?: throw Exception("Missing bwd for portal_pages[$page]")
                val m = (0 until mEntries.size).map { i ->
                    mEntries[i] ?: throw Exception("Missing m[$i] for portal_pages[$page]")
                }
                url.toHttpUrl().newBuilder()
                    .fragment("$hc:$bwd:${m.joinToString(",")}")
                    .build()
                    .toString()
            } else {
                url
            }
            Page(i, imageUrl = imgUrl)
        }
    }

    override fun getFilterList() = FilterList(CategoryFilter())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val IS_PORTAL = Regex("""isPortal\s*:\s*(true|false)""")
        private val IMG_PAGES = Regex("""imgs\[(\d+)][^{]*\{[^{]*'url'\s*:\s*'([^']+)'""")
        private val INDICES = Regex("""portal_pages\[(\d+)]\s*=\s*\{""")
        private val URL_MAP = Regex("""portal_pages\[(\d+)]\s*=\s*\{[^{]*'url'\s*:\s*'([^']+)'""")
        private val HC_MAP = Regex("""portal_pages\[(\d+)]\s*=\s*\{.*?'hc'\s*:\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
        private val BWD_MAP = Regex("""portal_pages\[(\d+)]\s*=\s*\{.*?'bwd'\s*:\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
        private val M_MAP = Regex("""portal_pages\[(\d+)]\.metadata\.m\[(\d+)]\s*=\s*'([^']+)'""")
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
