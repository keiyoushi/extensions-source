package eu.kanade.tachiyomi.extension.ko.jjaptoon

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Series titles are kept exactly as on the site (Korean).
 * Filter labels can be switched between Korean and English.
 */
@Source
abstract class Jjaptoon :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = true

    // Gentle rate limit — site returns 403 under heavy load.
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val preferences by getPreferencesLazy()

    private val useEnglishFilters: Boolean
        get() = preferences.getBoolean(PREF_FILTER_LANG_KEY, false)

    private val portalUrlPref by lazy {
        preferences.getString(PREF_PORTAL_KEY, DEFAULT_PORTAL_URL) ?: DEFAULT_PORTAL_URL
    }

    private val domainRegex = Regex("""https://www\.jjaptoon\d+\.com""")

    private var resolvedBaseUrl: String? = null

    // The site address changes over time (jjaptoon003 -> 004 -> ...). Fetch the official
    // domain-announcement page once per run to find the current address, falling back to
    // the stored base URL when it is unreachable, and update the stored base URL if it differs.
    private suspend fun currentBaseUrl(): String {
        resolvedBaseUrl?.let { return it }
        val resolved = runCatching {
            client.get("$portalUrlPref/data/domain.json").parseAs<PortalResponse>()
                .domain
                .takeIf(domainRegex::matches)
        }.getOrNull()

        if (resolved != null && resolved != baseUrl) {
            preferences.edit().putString("overrideBaseUrl", resolved).apply()
        }

        return (resolved ?: baseUrl).also { resolvedBaseUrl = it }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_FILTER_LANG_KEY
            title = "영어 필터 사용"
            summaryOn = "English filter labels"
            summaryOff = "한국어 필터 라벨"
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PORTAL_KEY
            title = "도메인 공지 주소"
            summary = "현재 사이트 주소를 알려주는 공지 URL"
            setDefaultValue(DEFAULT_PORTAL_URL)
        }.also(screen::addPreference)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("${currentBaseUrl()}/?selectedSort=popular&selectedType=general&comicsPage=$page")

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("${currentBaseUrl()}/?selectedType=general&comicsPage=$page")

    // ============================== Search ==============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val urlBuilder = "${currentBaseUrl()}/comics".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("search", query)
            filters.forEach { filter ->
                when (filter) {
                    is CategoryFilter -> filter.appendToUrl(this)
                    is StatusFilter -> filter.appendToUrl(this)
                    is TypeFilter -> filter.appendToUrl(this)
                    is PublisherFilter -> filter.appendToUrl(this)
                    is ScheduleFilter -> filter.appendToUrl(this)
                    else -> {}
                }
            }
            addQueryParameter("comicsPage", page.toString())
        }.build()
        return parseMangaList(urlBuilder.toString())
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val en = useEnglishFilters
        return FilterList(
            Filter.Header(
                if (en) {
                    "Search and filters are applied together"
                } else {
                    "검색어와 필터가 함께 적용됩니다"
                },
            ),
            Filter.Separator(),
            CategoryFilter(en),
            StatusFilter(en),
            TypeFilter(en),
            PublisherFilter(en),
            ScheduleFilter(en),
        )
    }

    // ============================== Details ==============================

    // Details and chapter list share the same page; always return both.
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(currentBaseUrl() + manga.url).asJsoup()
        val updatedManga = mangaDetailsParse(document).apply { url = manga.url }
        val chapterList = chapterListParse(document)
        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun mangaDetailsParse(document: org.jsoup.nodes.Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.text-3xl, h1.font-black")?.text().orEmpty()
        author = document.selectFirst("p:contains(작가:) span.font-bold")?.text()
            ?: document.selectFirst("p:contains(작가)")?.ownText()
        description = document.selectFirst("p.whitespace-pre-line")?.text()
        thumbnail_url = document.selectFirst("img.h-full.w-full.object-cover[src]")
            ?.absUrl("src")
        status = parseStatus(
            document.select("span.rounded-full").firstOrNull { el ->
                el.text().let {
                    it.contains("연재") || it.contains("완결") || it.contains("휴재")
                }
            }?.text(),
        )
        genre = document.select("span.rounded-full.bg-zinc-900")
            .map { it.text() }
            .filter { it.length > 1 }
            .joinToString()
            .ifBlank { null }
    }

    private fun parseStatus(text: String?): Int = when {
        text == null -> SManga.UNKNOWN
        text.contains("연재") -> SManga.ONGOING
        text.contains("완결") -> SManga.COMPLETED
        text.contains("휴재") -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    // All chapters are on a single page (no pagination).
    // wire:key distinguishes real chapter links from shortcut buttons.
    private fun chapterListParse(document: org.jsoup.nodes.Document): List<SChapter> = document.select("a[href^='/chapters/']")
        .filter { it.hasAttr("wire:key") }
        .map { element ->
            val href = element.absUrl("href")
            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = element.selectFirst(
                    "p.truncate.text-sm.font-black.text-zinc-100",
                )?.text()
                    ?: element.selectFirst("p.truncate")?.text()
                    ?: href.substringAfterLast("/")
                date_upload = parseDate(
                    element.selectFirst("p.mt-1.text-xs.text-zinc-600")?.text(),
                )
            }
        }

    // ============================== Pages ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(currentBaseUrl() + chapter.url).asJsoup()
        return document.select("img.select-none[src]")
            .mapIndexed { index, el -> Page(index, imageUrl = el.absUrl("src")) }
    }

    // ============================== Helpers ==============================

    // Server-side pagination via ?comicsPage=N; each page holds 36 series.
    private suspend fun parseMangaList(url: String): MangasPage {
        val document = client.get(url).asJsoup()
        val currentPage = url.toHttpUrl().queryParameter("comicsPage")?.toIntOrNull() ?: 1

        val mangas = document.select("a.group[href^='/comics/']")
            .filter { el ->
                el.attr("href")
                    .removePrefix("/comics/")
                    .substringBefore('?')
                    .all(Char::isDigit)
            }
            .map { el ->
                SManga.create().apply {
                    val img = el.selectFirst("img[alt]")
                    setUrlWithoutDomain(el.absUrl("href"))
                    title = img?.attr("alt")?.trim().orEmpty()
                    thumbnail_url = img?.absUrl("src")
                }
            }

        val maxPage = document.select("a[href*='comicsPage=']")
            .mapNotNull { link ->
                link.absUrl("href").toHttpUrl().queryParameter("comicsPage")?.toIntOrNull()
            }
            .maxOrNull() ?: currentPage

        return MangasPage(mangas, currentPage < maxPage && mangas.isNotEmpty())
    }

    private fun parseDate(text: String?): Long = if (text.isNullOrBlank()) {
        0L
    } else {
        runCatching {
            LocalDate.parse(text, dateFormat)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    @Serializable
    class PortalResponse(val domain: String = "")

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private const val PREF_FILTER_LANG_KEY = "pref_filter_lang_english"
        private const val PREF_PORTAL_KEY = "pref_portal_url"
        private const val DEFAULT_PORTAL_URL = "https://t.me/s/jjaptoon003"
    }
}
