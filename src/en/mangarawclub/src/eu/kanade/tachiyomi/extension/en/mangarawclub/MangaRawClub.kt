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
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaRawClub : ParsedHttpSource(), ConfigurableSource {

    override val id = 734865402529567092
    override val name = "MangaGeko"
    override val baseUrl = "https://www.mgeko.cc"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val preferences = getPreferences()
    private fun nsfw() = preferences.getBoolean(PREF_HIDE_NSFW, false)

    companion object {
        private const val altName = "Alternative Name:"
        private const val PREF_HIDE_NSFW = "pref_hide_nsfw"
        private val DATE_FORMATTER by lazy { SimpleDateFormat("MMMMM dd, yyyy, h:mm a", Locale.ENGLISH) }
        private val DATE_FORMATTER_2 by lazy { SimpleDateFormat("MMMMM dd, yyyy, h a", Locale.ENGLISH) }
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse-comics/data/?page=$page&sort=popular_all_time&safe_mode=${if (!nsfw()) "0" else "1"}", headers)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/browse-comics/data/?page=$page&sort=latest&safe_mode=${if (!nsfw()) "0" else "1"}", headers)
    }

    // Search
    override fun getFilterList(): FilterList = getFilters()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            // Query search
            val url = "$baseUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .build()
            return GET(url, headers)
        }

        // Filter search
        val url = "$baseUrl/browse-comics/data/".toHttpUrl().newBuilder().apply {
            val tagsIncl: MutableList<String> = mutableListOf()
            val genreIncl: MutableList<String> = mutableListOf()
            val genreExcl: MutableList<String> = mutableListOf()
            filters.forEach { filter ->
                if (!nsfw()) addQueryParameter("safe_mode", "0")

                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.selected)
                    }
                    is GenreFilter -> {
                        filter.state.forEach {
                            when {
                                it.isIncluded() -> genreIncl.add(it.name)
                                it.isExcluded() -> genreExcl.add(it.name)
                            }
                        }
                    }
                    is StatusFilter -> {
                        addQueryParameter("status", filter.selected)
                    }
                    is TypeFilter -> {
                        addQueryParameter("type", filter.selected)
                    }
                    is ChapterMinFilter -> {
                        val trimmed = filter.state.trim()
                        if (trimmed.isNotBlank()) {
                            addQueryParameter("min_chapters", trimmed)
                        }
                    }
                    is ChapterMaxFilter -> {
                        val trimmed = filter.state.trim()
                        if (trimmed.isNotBlank()) {
                            addQueryParameter("max_chapters", trimmed)
                        }
                    }
                    is RatingFilter -> {
                        val trimmed = filter.state.trim()
                        if (trimmed.isNotBlank()) {
                            val value = trimmed.toDoubleOrNull() ?: 0.0
                            addQueryParameter("min_rating", (value * 10).toInt().toString())
                        }
                    }
                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            filter.state.split(",").filter(String::isNotBlank).map { tag ->
                                tagsIncl.add(tag.trim())
                            }
                        }
                    }
                    is ExtraFilter -> {
                        val (activeFilters, _) = filter.state.partition { stIt -> stIt.state }
                        activeFilters.forEach {
                            addQueryParameter(it.value, "1")
                        }
                    }
                    else -> {}
                }
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("include_genres", genreIncl.joinToString(","))
            addQueryParameter("exclude_genres", genreExcl.joinToString(","))
            addQueryParameter("tags", tagsIncl.joinToString(","))
        }.build()

        return GET(url, headers)
    }

    // Selectors
    override fun searchMangaSelector() = ".comic-card"
    override fun popularMangaSelector() = searchMangaSelector()
    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Data>()

        with(data) {
            val document = Jsoup.parse(results_html)
            val mangas = document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }

            val hasNextPage = page < num_pages
            return MangasPage(mangas, hasNextPage)
        }
    }
    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Manga from Element
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".comic-card__title")!!.ownText()
        thumbnail_url = element.select(".comic-card__cover img").attr("abs:data-src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        document.selectFirst(".novel-header") ?: throw Exception("Page not found")

        author = document.selectFirst(".author a")?.attr("title")?.trim()?.takeIf { it.lowercase() != "updating" }

        description = buildString {
            document.selectFirst(".description")?.text()?.substringAfter("Summary is")?.trim()?.let {
                append(it)
            }
            document.selectFirst(".alternative-title")?.ownText()?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "updating" }?.let {
                append("\n\n$altName ${it.trim()}")
            }
        }

        genre = document.select(".categories a[href*=genre]").joinToString(", ") {
            it.ownText().trim()
                .split(" ").joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { c -> c.uppercase() }
                }
        }

        status = when {
            document.select("div.header-stats strong.completed").isNotEmpty() -> SManga.COMPLETED
            document.select("div.header-stats strong.ongoing").isNotEmpty() -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.selectFirst(".cover img")?.let { img ->
            img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src")
        } ?: thumbnail_url
    }

    // Chapters
    override fun chapterListSelector() = "ul.chapter-list > li"

    override fun chapterListRequest(manga: SManga): Request {
        val url = baseUrl + manga.url + "all-chapters/"
        return GET(url, headers)
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))

        val name = element.selectFirst(".chapter-title,.chapter-number")!!.ownText().removeSuffix("-eng-li")
        this.name = "Chapter $name"

        date_upload = parseChapterDate(element.select(".chapter-update").attr("datetime"))
    }

    private fun parseChapterDate(string: String): Long {
        // "April 21, 2021, 4:05 p.m."
        val date = string.replace(".", "").replace("Sept", "Sep")
        return runCatching { DATE_FORMATTER.parse(date)?.time }.getOrNull()
            ?: runCatching { DATE_FORMATTER_2.parse(date)?.time }.getOrNull() ?: 0L
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".page-in img[onerror]").mapIndexed { i, it ->
            Page(i, imageUrl = it.attr("src"))
        }
    }

    // Settings
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_NSFW
            title = "Hide NSFW"
            summary = "Hides NSFW entries"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}

@Serializable
class Data(
    val results_html: String,
    val page: Int,
    val num_pages: Int,
)
