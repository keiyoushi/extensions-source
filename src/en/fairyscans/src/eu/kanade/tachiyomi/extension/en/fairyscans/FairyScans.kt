package eu.kanade.tachiyomi.extension.en.fairyscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Calendar

@Source
abstract class FairyScans :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    private val preferences by getPreferencesLazy()

    private var filterNonce: String? = null
    private var loadMoreNonce: String? = null

    private fun getNonce(page: Int): String {
        if (filterNonce == null || loadMoreNonce == null) {
            val html = client.newCall(GET("$baseUrl/manga/", headers)).execute().use { response ->
                response.body.string()
            }

            filterNonce = NONCE_REGEX.find(html.substringAfter("greedArchiveBrowse"))?.groupValues?.get(1)
            loadMoreNonce = NONCE_REGEX.find(html.substringAfter("greedArchiveMore"))?.groupValues?.get(1)
        }
        return (if (page == 1) filterNonce else loadMoreNonce)
            ?: throw Exception("Could not find required nonces for filtering")
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val sortFilter = SortFilter().apply { state = Filter.Sort.Selection(1, false) }
        return searchMangaRequest(page, "", FilterList(sortFilter))
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val sortFilter = SortFilter().apply { state = Filter.Sort.Selection(0, false) }
        return searchMangaRequest(page, "", FilterList(sortFilter))
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val nonce = getNonce(page)
        val action = if (page == 1) "greed_filter_series" else "greed_archive_load_more"

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        val sort = sortFilter?.state?.let { SORT_VALUES[it.index] } ?: "latest"
        val order = sortFilter?.state?.let { if (it.ascending) "asc" else "desc" } ?: "desc"
        val status = statusFilter?.state?.let { STATUS_VALUES[it] } ?: "all"
        val type = typeFilter?.state?.let { TYPE_VALUES[it] } ?: "all"
        val genre = genreFilter?.state?.let { GENRE_VALUES[it] } ?: ""

        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("action", action)
            .addFormDataPart("nonce", nonce)
            .addFormDataPart("page", page.toString())
            .addFormDataPart("per_initial", "20")
            .addFormDataPart("per_more", "10")
            .addFormDataPart("filters[sort]", sort)
            .addFormDataPart("filters[order]", order)
            .addFormDataPart("filters[status]", status)
            .addFormDataPart("filters[type]", type)
            .addFormDataPart("filters[genre]", genre)
            .addFormDataPart("filters[creator]", "")
            .addFormDataPart("filters[s]", query)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = try {
            response.parseAs<BrowseResponseDto>()
        } catch (e: Exception) {
            // Nullify nonces if response breaks, they could have expired.
            filterNonce = null
            loadMoreNonce = null
            throw e
        }

        if (!dto.success) {
            filterNonce = null
            loadMoreNonce = null
            throw Exception("Failed to fetch search results from the source")
        }

        val document = Jsoup.parseBodyFragment(dto.gridHtml, baseUrl)
        val mangas = document.select("article").mapNotNull { element ->
            val isNovel = element.selectFirst(".greed-browse-card-format-badge--novel") != null ||
                element.selectFirst(".greed-browse-card-format-badge")?.text()?.lowercase() == "novel"

            if (isNovel) return@mapNotNull null

            SManga.create().apply {
                val a = element.selectFirst("h2 a, a.greed-browse-card-image, a.greed-archive-cover")!!
                setUrlWithoutDomain(a.absUrl("href"))
                title = element.selectFirst("h2")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, dto.hasMore)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".greed-series-title, h1")!!.text()

            val jsonLd = document.selectFirst("script[type=application/ld+json]")?.data()
            if (jsonLd != null) {
                author = AUTHOR_REGEX.find(jsonLd)?.groupValues?.get(1)
            }

            description = document.selectFirst(".greed-series-description")?.text()
            thumbnail_url = document.selectFirst(".greed-series-cover-img")?.absUrl("src")

            genre = document.select(".greed-series-genre").joinToString { it.text() }

            val statusText = document.selectFirst(".fairy-series-clean__meta-item--status .fairy-series-clean__meta-v")?.text()?.lowercase()
            status = when {
                statusText == null -> SManga.UNKNOWN
                statusText.contains("ongoing") -> SManga.ONGOING
                statusText.contains("completed") -> SManga.COMPLETED
                statusText.contains("hiatus") -> SManga.ON_HIATUS
                statusText.contains("dropped") -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val hidePremium = preferences.getBoolean(PREF_HIDE_PREMIUM_CHAPTERS, true)

        return document.select(".greed-series-chapter").mapNotNull { element ->
            val isLocked = element.hasClass("is-locked")

            if (isLocked && hidePremium) {
                return@mapNotNull null
            }

            val rawName = element.selectFirst(".greed-series-chapter-title")!!.text()

            // Site gives us an accurate sort index inside the data-chapter-order attribute
            val orderAttr = element.attr("data-chapter-order").toFloatOrNull()
            val orderFallback = chapterNumberRegex.find(rawName)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
            val finalOrder = orderAttr ?: orderFallback

            val chapter = SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = if (isLocked) "\uD83D\uDD12 $rawName" else rawName
                chapter_number = finalOrder
                date_upload = parseRelativeDate(element.selectFirst(".greed-series-chapter-date")?.text())
            }
            Pair(finalOrder, chapter)
        }
            .sortedByDescending { it.first } // Fixes site's out-of-order problem
            .map { it.second }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scriptData = document.selectFirst("script:containsData(ts_reader.run)")?.data()
            ?: throw Exception("Reader script not found")

        val jsonStr = scriptData.substringAfter("ts_reader.run(").substringBeforeLast(");")
        val dto = jsonStr.parseAs<ReaderDto>()

        return dto.images.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    // ============================= Utilities =============================

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        val split = dateStr.split(" ")
        if (split.size < 2) return 0L
        val count = split[0].toIntOrNull() ?: return 0L
        val unit = split[1].lowercase()
        val cal = Calendar.getInstance()

        when {
            unit.contains("year") -> cal.add(Calendar.YEAR, -count)
            unit.contains("month") -> cal.add(Calendar.MONTH, -count)
            unit.contains("week") -> cal.add(Calendar.WEEK_OF_YEAR, -count)
            unit.contains("day") -> cal.add(Calendar.DAY_OF_YEAR, -count)
            unit.contains("hour") -> cal.add(Calendar.HOUR_OF_DAY, -count)
            unit.contains("minute") || unit.contains("min") -> cal.add(Calendar.MINUTE, -count)
            unit.contains("second") || unit.contains("sec") -> cal.add(Calendar.SECOND, -count)
            else -> return 0L
        }
        return cal.timeInMillis
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM_CHAPTERS
            title = "Hide premium chapters"
            summary = "Hide chapters that require coins/payment to read. If disabled, premium chapters will be marked with \uD83D\uDD12."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_HIDE_PREMIUM_CHAPTERS = "pref_hide_premium_chapters"
        private val chapterNumberRegex = """(?i)(?:chapter|ch)\s*(\d+(?:\.\d+)?)""".toRegex()
        private val NONCE_REGEX = """"nonce"\s*:\s*"([^"]+)"""".toRegex()
        private val AUTHOR_REGEX = """"author"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"""".toRegex()
    }
}
