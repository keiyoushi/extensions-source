package eu.kanade.tachiyomi.extension.all.nijiero

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Nijiero : ParsedHttpSource() {
    override val name = "Nijiero"
    override val lang = "all"
    override val supportsLatest = true

    override val baseUrl = "https://nijiero-ch.com"

    override val client = network.cloudflareClient
	
    class TagFilter : Filter.Select<String>("Category", nijieroTags)

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#entry > ul > li a[href]").mapIndexed { i, linkElement ->
            val linkUrl = linkElement.attr("href").removeSuffix(".webp")
            Page(i, document.location(), linkUrl)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
	
    private val spaceRegex = Regex("""\s+""")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagIdx: Int = (filters.last() as TagFilter).state

        val keyword = when {
            query.isBlank() -> nijieroTags[tagIdx]
            else -> query
        }.replace(spaceRegex, "-").lowercase()

        val uniqueParam = System.currentTimeMillis()
        var url = baseUrl.toHttpUrl().newBuilder()
            .addEncodedPathSegment("category")
            .addEncodedPathSegment(keyword)
            .addEncodedPathSegment("page")
            .addEncodedPathSegment(page.toString())
            .addQueryParameter("refresh", uniqueParam.toString())
            .build()
		
        // if 404, recheck
        if (client.newCall(GET(url, headers)).execute().code == 404) {
            url = baseUrl.toHttpUrl().newBuilder()
                .addEncodedPathSegment("tag")
                .addEncodedPathSegment(keyword)
                .addEncodedPathSegment("page")
                .addEncodedPathSegment(page.toString())
                .addQueryParameter("refresh", uniqueParam.toString())
                .build()
            // if there is a better solution just *tell me* .
        }

        return GET(url, headers)
    }
    override fun searchMangaNextPageSelector() = ".next.page-numbers"
    override fun searchMangaSelector() = ".contentList > div:has(a)"
    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a").let { link ->
            element.selectFirst("img")?.let {
                thumbnail_url = imageFromElement(it)
            }
            title = link.attr("title")
            setUrlWithoutDomain(link.attr("href"))
            initialized = true
        }
    }
	
    override fun popularMangaRequest(page: Int): Request {
        val uniqueParam = System.currentTimeMillis()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("ranking.html")
            .addQueryParameter("refresh", uniqueParam.toString())
            .build()

        return GET(url, headers)
    }
    override fun popularMangaNextPageSelector() = null
    override fun popularMangaSelector() = "#mainContent .allRunkingArea.tabContent.cf > div"
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request {
        val uniqueParam = System.currentTimeMillis()
        var url = baseUrl.toHttpUrl().newBuilder()
            .addEncodedPathSegment("page")
            .addEncodedPathSegment(page.toString())
            .addQueryParameter("refresh", uniqueParam.toString())
            .build()

        return GET(url, headers)
    }
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesSelector() = "#mainContent > div:has(a)"
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.arrow.mb0 h1.type01_hl")?.text() ?: document.title()
        thumbnail_url = document.select("meta[property=og:image]").attr("content")
        status = SManga.COMPLETED
        genre = getGenres(document)
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }
    private fun getGenres(document: Document): String {
        val genres = document.select("dl.cf:contains(カテゴリ) a").map {
            it.attr("href").trimEnd('/').split("/").last()
        }.map {
            it.replace("-", " ")
        }
        return genres.joinToString()
    }
    protected open fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            else -> element.attr("abs:src")
        }
    }

    override fun chapterListSelector() = "html"
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("link[rel=canonical]").attr("abs:href"))
        name = "GALLERY"
        date_upload = parseDate(element.selectFirst("div.postInfo.cf div.postDate.cf time.entry-date.date.published.updated")?.attr("datetime").orEmpty())
    }
	
    private fun parseDate(dateStr: String): Long {
        return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }
	
    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("You can search for a tag or category, anything else will result in 404(NOT FOUND)."),
        Filter.Header("To use category, make sure the search box is empty."),
        TagFilter(),
    )
}
