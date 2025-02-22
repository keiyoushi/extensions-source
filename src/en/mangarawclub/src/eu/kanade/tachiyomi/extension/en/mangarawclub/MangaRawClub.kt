package eu.kanade.tachiyomi.extension.en.mangarawclub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaRawClub : ParsedHttpSource() {

    override val id = 734865402529567092
    override val name = "MangaGeko"
    override val baseUrl = "https://www.mgeko.cc"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val altName = "Alternative Name:"

        private val DATE_FORMATTER by lazy { SimpleDateFormat("MMMMM dd, yyyy, h:mm a", Locale.ENGLISH) }
        private val DATE_FORMATTER_2 by lazy { SimpleDateFormat("MMMMM dd, yyyy, h a", Locale.ENGLISH) }
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse-comics/?results=$page&filter=views", headers)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/jumbo/manga/?results=$page", headers)
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
        val url = "$baseUrl/browse-comics/".toHttpUrl().newBuilder().apply {
            val tagsIncl: MutableList<String> = mutableListOf()
            val tagsExcl: MutableList<String> = mutableListOf()
            val genreIncl: MutableList<String> = mutableListOf()
            val genreExcl: MutableList<String> = mutableListOf()
            filters.forEach { filter ->
                when (filter) {
                    is SelectFilter -> addQueryParameter("filter", filter.vals[filter.state])
                    is GenreFilter -> {
                        filter.state.forEach {
                            when {
                                it.isIncluded() -> genreIncl.add(it.name)
                                it.isExcluded() -> genreExcl.add(it.name)
                            }
                        }
                    }
                    is ChapterFilter -> addQueryParameter("minchap", filter.state)
                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            filter.state.split(",").filter(String::isNotBlank).map { tag ->
                                val trimmed = tag.trim()
                                when {
                                    trimmed.startsWith('-') -> tagsExcl.add(trimmed.removePrefix("-"))
                                    else -> tagsIncl.add(trimmed)
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
            addQueryParameter("results", page.toString())
            addQueryParameter("genre_included", genreIncl.joinToString(","))
            addQueryParameter("genre_excluded", genreExcl.joinToString(","))
            addQueryParameter("tags_include", tagsIncl.joinToString(","))
            addQueryParameter("tags_exclude", tagsExcl.joinToString(","))
        }.build()

        return GET(url, headers)
    }

    // Selectors
    override fun searchMangaSelector() = "ul.novel-list > li.novel-item"
    override fun popularMangaSelector() = searchMangaSelector()
    override fun latestUpdatesSelector() = "ul.novel-list.chapters > li.novel-item"

    override fun searchMangaNextPageSelector() = ".paging .mg-pagination-chev:last-child:not(.chev-disabled)"
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // Manga from Element
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".novel-title")!!.ownText()
        thumbnail_url = element.select(".novel-cover img").attr("abs:data-src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        document.selectFirst(".novel-header") ?: throw Exception("Page not found")

        author = document.selectFirst(".author a")?.attr("title")?.trim()?.takeIf { it.lowercase() != "updating" }

        description = buildString {
            document.selectFirst(".description")?.ownText()?.substringAfter("Summary is")?.trim()?.let {
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

        val name = element.select(".chapter-title").text().removeSuffix("-eng-li")
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

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
