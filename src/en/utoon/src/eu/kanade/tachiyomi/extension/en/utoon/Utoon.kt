package eu.kanade.tachiyomi.extension.en.utoon

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Utoon :
    Madara("Utoon", "https://utoon.net", "en"),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/${searchPage(page)}?orderby=popular", headers)

    override fun popularMangaSelector() = ".agrid .acard"

    override fun popularMangaNextPageSelector() = ".pager span.on + a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst(".ac-t")!!.text()
        thumbnail_url = element.selectFirst("img")?.let { imageFromElement(it) }
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/${searchPage(page)}", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga/${searchPage(page)}".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        if (sortFilter != null) {
            val value = sortFilter.toUriPart()
            if (value.isNotEmpty()) url.addQueryParameter("orderby", value)
        }

        val statusFilter = filters.firstInstanceOrNull<UtoonStatusFilter>()
        if (statusFilter != null) {
            val value = statusFilter.toUriPart()
            if (value.isNotEmpty()) url.addQueryParameter("status", value)
        }

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        if (genreFilter != null) {
            val value = genreFilter.toUriPart()
            if (value.isNotEmpty()) url.addQueryParameter("genre", value)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // ============================== Details ==============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".htitle")!!.text()
        thumbnail_url = document.selectFirst(".poster img")?.let { imageFromElement(it) }
        genre = document.select(".genres .genre").joinToString { it.text() }.takeIf { it.isNotEmpty() }
        description = document.selectFirst(".syn")?.text()

        document.select(".sinfo-grid .sir").forEach { element ->
            val label = element.selectFirst(".l")?.text().orEmpty()
            val value = element.selectFirst(".v")?.text().orEmpty()
            when (label) {
                "Author" -> author = value
                "Artist" -> artist = value
                "Status" -> status = when (value.lowercase()) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    "on hold" -> SManga.ON_HIATUS
                    "canceled" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }

        if (status == SManga.UNKNOWN) {
            val hinfoStatus = document.select(".hinfo .hi").map { it.text().lowercase() }
            status = when {
                hinfoStatus.any { it.contains("ongoing") } -> SManga.ONGOING
                hinfoStatus.any { it.contains("completed") } -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED_CHAPTERS, PREF_SHOW_LOCKED_CHAPTERS_DEFAULT)

        val script = document.selectFirst("script:containsData(var CH=)")?.data()
            ?: return emptyList()

        val jsonString = script.substringAfter("var CH=").substringBefore(";")

        val chapters = try {
            jsonString.parseAs<List<Dto>>()
        } catch (_: Exception) {
            emptyList()
        }

        return chapters.mapNotNull { dto ->
            if (!showLocked && dto.isLocked) return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(dto.url)
                name = if (dto.isLocked) "\uD83D\uDD12 ${dto.label}" else dto.label
                date_upload = parseChapterDate(dto.ago)
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst(".zx-locked__card") != null) {
            return emptyList()
        }
        return super.pageListParse(document)
    }

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        UtoonStatusFilter(),
        GenreFilter(),
    )

    // ============================= Utilities =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED_CHAPTERS
            title = "Show locked/premium chapters"
            summary = "Show chapters that require coins to read. They will be marked with \uD83D\uDD12"
            setDefaultValue(PREF_SHOW_LOCKED_CHAPTERS_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_SHOW_LOCKED_CHAPTERS = "pref_show_locked_chapters"
        private const val PREF_SHOW_LOCKED_CHAPTERS_DEFAULT = false
    }
}
