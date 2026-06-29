package eu.kanade.tachiyomi.extension.en.mangamob

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
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class Comivex :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val hideStaleExploreEntries: Boolean
        get() = preferences.getBoolean(PREF_HIDE_STALE, true)

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/explore/?sort_by=Views&results=$page&ajax=1", headers)

    override fun popularMangaParse(response: Response): MangasPage = exploreParse(response)

    // =============================== Latest ===============================
    // /latest/ orders by chapter publication; /explore/?sort_by=Updated
    // orders by metadata mtime and surfaces stale series.

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("article.u-card")
            .mapNotNull(::parseLatestCard)
            .distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    private fun parseLatestCard(card: Element): SManga? {
        val link = card.selectFirst("a.u-card__title") ?: return null
        val name = link.attr("title").ifBlank { link.text() }.ifBlank { return null }
        return SManga.create().apply {
            setUrlWithoutDomain(link.attr("abs:href"))
            title = name
            thumbnail_url = card.selectFirst("img.u-card__img")?.attr("abs:src")
        }
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/explore/".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) addQueryParameter("search", query)

            // Site lists formats and content genres under one `genre_included`
            // param; Type wins when both are set.
            val genreIncluded = filters.firstInstanceOrNull<TypeFilter>()?.selectedValue()
                ?.takeIf { it.isNotEmpty() }
                ?: filters.firstInstanceOrNull<GenreFilter>()?.selectedValue().orEmpty()

            addQueryParameter("genre_included", genreIncluded)
            addQueryParameter("sort_by", filters.firstInstanceOrNull<SortFilter>()?.selectedValue() ?: "Views")
            addQueryParameter("status", filters.firstInstanceOrNull<StatusFilter>()?.selectedValue().orEmpty())
            addQueryParameter("results", page.toString())
            addQueryParameter("ajax", "1")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = exploreParse(response)

    override fun getFilterList() = FilterList(
        GenreFilter(),
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
    )

    private fun exploreParse(response: Response): MangasPage {
        val applyStaleFilter = hideStaleExploreEntries &&
            response.request.url.queryParameter("sort_by") == "Updated"

        val mangas = response.asJsoup().select("article.manga-card").mapNotNull { card ->
            val link = card.selectFirst("a.card-cover") ?: return@mapNotNull null
            val url = link.attr("abs:href")
            if (applyStaleFilter && url.seriesId() in STALE_EXPLORE_IDS) return@mapNotNull null
            val name = card.selectFirst(".card-title a")?.text() ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(url)
                title = name
                thumbnail_url = card.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".md-title")?.text() ?: throw Exception("Title not found")
            author = document.selectFirst(".md-author span")?.text()
            description = document.selectFirst("#synopsis")?.text()
            genre = document.select(".md-genres a.md-genre-pill").joinToString(", ") { it.text() }
            thumbnail_url = document.selectFirst(".md-cover-wrap img.md-cover")?.attr("abs:src")
            status = parseStatus(document.selectFirst(".md-status")?.text())
        }
    }

    private fun parseStatus(status: String?): Int {
        val lower = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "ongoing" in lower -> SManga.ONGOING
            "completed" in lower -> SManga.COMPLETED
            "hiatus" in lower -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(".ch-list .ch-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.selectFirst("a.ch-link")!!.attr("abs:href"))
            name = element.selectFirst(".ch-num")?.text() ?: ""
            date_upload = parseRelativeDate(element.selectFirst(".ch-date")?.text() ?: "")
        }
    }

    private fun parseRelativeDate(dateStr: String): Long {
        val now = Calendar.getInstance()
        var matched = false
        RELATIVE_DATE_REGEX.findAll(dateStr).forEach { match ->
            matched = true
            val amount = match.groupValues[1].toInt()
            when (match.groupValues[2]) {
                "year" -> now.add(Calendar.YEAR, -amount)
                "month" -> now.add(Calendar.MONTH, -amount)
                "week" -> now.add(Calendar.WEEK_OF_YEAR, -amount)
                "day" -> now.add(Calendar.DAY_OF_YEAR, -amount)
                "hour" -> now.add(Calendar.HOUR_OF_DAY, -amount)
                "minute" -> now.add(Calendar.MINUTE, -amount)
            }
        }
        return if (matched) now.timeInMillis else 0L
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("#chapter-images .page-wrapper img").mapIndexed { index, img ->
        Page(index, imageUrl = img.attr("abs:src"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_STALE
            title = "Hide stuck 'Recently Updated' entries"
            summary = "Skip manga that have been pinned to the top of Explore's 'Recently Updated' " +
                "sort for months with no new chapters. The Latest tab is unaffected."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    // =============================== Helpers ==============================

    private fun String.seriesId(): String? = SERIES_ID_REGEX.find(this)?.groupValues?.get(1)

    companion object {
        private const val PREF_HIDE_STALE = "pref_hide_stale_explore_entries"

        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s+(year|month|week|day|hour|minute)s?""")
        private val SERIES_ID_REGEX = Regex("""/series/(\d+)[-/]""")

        // Pinned at the top of `sort_by=Updated` for months with no new chapters
        // (upstream metadata-mtime bug). Numeric id is stable across slug renames.
        private val STALE_EXPLORE_IDS = setOf(
            "7805", // Even Though I'm a Level-0 Useless Explorer …
            "8025", // Saikyou Demodori Chuunen Boukensha Wa …
            "8168", // All-Class Awakening: God Slayer II
            "8169", // Toritsu Gyakujuuji Byouin
            "8176", // Fight Delivery
            "8188", // The Strongest Sage With Zero Magic Power …
        )
    }
}
