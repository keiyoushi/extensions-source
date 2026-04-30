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
import keiyoushi.utils.getPreferences
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
import java.util.concurrent.TimeUnit

class MangaRawClub :
    HttpSource(),
    ConfigurableSource {

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
        private const val ALT_NAME = "Alternative Name:"
        private const val PREF_HIDE_NSFW = "pref_hide_nsfw"
        private val DATE_FORMATTER by lazy { SimpleDateFormat("MMMMM dd, yyyy, h:mm a", Locale.ENGLISH) }
        private val DATE_FORMATTER_2 by lazy { SimpleDateFormat("MMMMM dd, yyyy, h a", Locale.ENGLISH) }
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse-comics/data/?page=$page&sort=popular_all_time&safe_mode=${if (!nsfw()) "0" else "1"}", headers)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse-comics/data/?page=$page&sort=latest&safe_mode=${if (!nsfw()) "0" else "1"}", headers)

    // Search
    override fun getFilterList(): FilterList = getFilters()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank() && filters.all { it.isDefault() }) {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
                addQueryParameter("search", query.trim())
                addQueryParameter("results", page.toString())
            }.build()
            return GET(url, headers)
        }

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
                            filter.state.split(",").filter(String::isNotBlank).forEach { tag ->
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
            addQueryParameter("q", query)
        }.build()

        return GET(url, headers)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".comic-card__title a")!!.text()
        thumbnail_url = element.selectFirst(".comic-card__cover img")!!.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("search")) {
            val document = response.asJsoup()
            val mangas = document.select(".novel-item").map { element ->
                SManga.create().apply {
                    title = element.selectFirst(".novel-title")!!.text()
                    thumbnail_url = element.selectFirst(".novel-cover img")!!.let { it ->
                        it.absUrl("data-src").takeIf { it.isNotEmpty() } ?: it.absUrl("src")
                    }
                    setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                }
            }
            val hasNextPage = document.selectFirst("nav.paging a:contains(Next)") != null
            return MangasPage(mangas, hasNextPage)
        }

        val data = response.parseAs<Dto>()
        val document = Jsoup.parseBodyFragment(data.resultsHtml, baseUrl)
        val mangas = document.select(".comic-card").map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = data.page < data.numPages
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        document.selectFirst(".novel-header") ?: throw Exception("Page not found")

        author = document.selectFirst(".author a")?.attr("title")?.trim()?.takeIf { it.lowercase() != "updating" }

        description = buildString {
            document.selectFirst(".description")?.text()?.substringAfter("Summary is")?.trim()?.let {
                append(it)
            }
            document.selectFirst(".alternative-title")?.let {
                if (it.ownText().isNotEmpty()) {
                    append("\n\n", ALT_NAME)
                    it.ownText().split(",").filter { t -> t.isNotBlank() && t.trim().lowercase() != "updating" }.forEach { name ->
                        append("\n", "- ${name.trim()}")
                    }
                }
            }
        }

        genre = document.select(".categories a[href*=genre]").joinToString(", ") {
            it.ownText()
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
    override fun chapterListRequest(manga: SManga): Request {
        val url = baseUrl + manga.url + "all-chapters/"
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.chapter-list > li").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.select("a").attr("href"))

                val name = element.selectFirst(".chapter-title,.chapter-number")!!.ownText().removeSuffix("-eng-li")
                this.name = "Chapter $name"

                date_upload = parseChapterDate(element.select(".chapter-update").attr("datetime"))
            }
        }
    }

    private fun parseChapterDate(string: String): Long {
        val date = string.replace(".", "").replace("Sept", "Sep")
        return DATE_FORMATTER.tryParse(date).takeIf { it != 0L } ?: DATE_FORMATTER_2.tryParse(date)
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".page-in img[onerror]").mapIndexed { i, it ->
            Page(i, imageUrl = it.attr("src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Settings
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_NSFW
            title = "Hide NSFW"
            summary = "Hides NSFW entries"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }
}
