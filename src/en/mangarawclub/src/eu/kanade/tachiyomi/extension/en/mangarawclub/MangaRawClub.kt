package eu.kanade.tachiyomi.extension.en.mangarawclub

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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private val DATE_FORMATTER = SimpleDateFormat("MMMM dd, yyyy, h:mm a", Locale.ENGLISH)
private val DATE_FORMATTER_2 = SimpleDateFormat("MMMM dd, yyyy, h a", Locale.ENGLISH)

@Source
abstract class MangaRawClub :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true
    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(10.seconds)
        .readTimeout(30.seconds)
        .build()

    private val preferences by getPreferencesLazy()
    private fun nsfw() = preferences.getBoolean(PREF_HIDE_NSFW, false)

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse-comics/data/?page=$page&sort=popular_all_time&safe_mode=${if (nsfw()) "1" else "0"}", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse-comics/data/?page=$page&sort=latest&safe_mode=${if (nsfw()) "1" else "0"}", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search ===============================
    override fun getFilterList(): FilterList = getFilters()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Fallback directly to autocomplete query if only search term is provided
        if (query.isNotBlank() && (filters.isEmpty() || filters.all { it.isDefault() })) {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
                addQueryParameter("search", query.trim())
                addQueryParameter("results", page.toString())
            }.build()

            return GET(url, headers)
        }

        val url = "$baseUrl/browse-comics/data/".toHttpUrl().newBuilder().apply {
            val tagsIncl = mutableListOf<String>()
            val genreIncl = mutableListOf<String>()
            val genreExcl = mutableListOf<String>()

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.selected)
                    is GenreFilter -> {
                        filter.state.forEach {
                            when {
                                it.isIncluded() -> genreIncl.add(it.name)
                                it.isExcluded() -> genreExcl.add(it.name)
                            }
                        }
                    }
                    is StatusFilter -> addQueryParameter("status", filter.selected)
                    is TypeFilter -> addQueryParameter("type", filter.selected)
                    is ChapterMinFilter -> {
                        if (filter.state.isNotEmpty()) addQueryParameter("min_chapters", filter.state.trim())
                    }
                    is ChapterMaxFilter -> {
                        if (filter.state.isNotEmpty()) addQueryParameter("max_chapters", filter.state.trim())
                    }
                    is RatingFilter -> {
                        if (filter.state.isNotEmpty()) {
                            val value = filter.state.toDoubleOrNull() ?: 0.0
                            addQueryParameter("min_rating", (value * 10).toInt().toString())
                        }
                    }
                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            filter.state.split(",").filter { it.isNotEmpty() }.forEach { tag ->
                                tagsIncl.add(tag.trim())
                            }
                        }
                    }
                    is ExtraFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter(it.value, "1")
                        }
                    }
                    else -> {}
                }
            }

            addQueryParameter("safe_mode", if (nsfw()) "1" else "0")
            addQueryParameter("page", page.toString())
            if (genreIncl.isNotEmpty()) addQueryParameter("include_genres", genreIncl.joinToString(","))
            if (genreExcl.isNotEmpty()) addQueryParameter("exclude_genres", genreExcl.joinToString(","))
            if (tagsIncl.isNotEmpty()) addQueryParameter("tags", tagsIncl.joinToString(","))
            addQueryParameter("q", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("search")) {
            val document = response.asJsoup()
            val mangas = document.select(".novel-item").map { element ->
                SManga.create().apply {
                    title = element.selectFirst(".novel-title")!!.text()
                    thumbnail_url = element.selectFirst(".novel-cover img")?.let {
                        it.absUrl("data-src").ifEmpty { it.absUrl("src") }
                    }
                    setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                }
            }
            val hasNextPage = document.selectFirst("nav.paging a:contains(Next)") != null
            return MangasPage(mangas, hasNextPage)
        }

        val data = response.parseAs<Dto>()
        val document = Jsoup.parseBodyFragment(data.html, baseUrl)
        val mangas = document.select(".comic-card").map { searchMangaFromElement(it) }

        return MangasPage(mangas, data.hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".comic-card__title a")!!.text()
        thumbnail_url = element.selectFirst(".comic-card__cover img")?.let {
            it.absUrl("data-src").ifEmpty { it.absUrl("src") }
        }
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        document.selectFirst(".novel-header") ?: throw Exception("Page not found")

        author = document.selectFirst(".author a")?.attr("title")?.trim()?.takeIf { it.lowercase() != "updating" }

        description = buildString {
            document.selectFirst(".description")?.text()?.substringAfter("Summary is")?.let {
                append(it)
            }

            parseAltNames(document.selectFirst(".alternative-title")?.ownText())?.let { altNames ->
                if (isNotEmpty()) append("\n\n")
                append(ALT_NAME)
                altNames.forEach { name -> append("\n- $name") }
            }
        }

        genre = document.select(".categories a[href*=genre]").joinToString {
            it.ownText().split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
        }

        status = when {
            document.selectFirst("div.header-stats strong.completed") != null -> SManga.COMPLETED
            document.selectFirst("div.header-stats strong.ongoing") != null -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.selectFirst(".cover img")?.let {
            it.absUrl("data-src").ifEmpty { it.absUrl("src") }
        }
    }

    private fun parseAltNames(raw: String?): List<String>? {
        if (raw.isNullOrEmpty()) return null

        val separator = if (ALT_NAME_BULLET_SEMICOLON_REGEX.containsMatchIn(raw)) {
            ALT_NAME_BULLET_SEMICOLON_REGEX
        } else {
            ALT_NAME_COMMA_REGEX
        }

        return raw.split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() != "updating" }
            .takeIf { it.isNotEmpty() }
    }

    // ============================= Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url + "all-chapters/", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.chapter-list > li").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))

                val chapterName = element.selectFirst(".chapter-title, .chapter-number")!!.ownText().removeSuffix("-eng-li")
                name = "Chapter $chapterName"

                date_upload = parseChapterDate(element.selectFirst(".chapter-update")?.attr("datetime"))
            }
        }
    }

    private fun parseChapterDate(string: String?): Long {
        if (string.isNullOrEmpty()) return 0L
        val date = string.replace(".", "").replace("Sept", "Sep")
        return DATE_FORMATTER.tryParse(date).takeIf { it != 0L } ?: DATE_FORMATTER_2.tryParse(date)
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#chapter-reader img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================= Utilities =============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_NSFW
            title = "Hide NSFW"
            summary = "Hides NSFW entries"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val ALT_NAME = "Alternative Names:"
        private const val PREF_HIDE_NSFW = "pref_hide_nsfw"
        private val ALT_NAME_BULLET_SEMICOLON_REGEX = Regex("[•;]")
        private val ALT_NAME_COMMA_REGEX = Regex(",")
    }
}
