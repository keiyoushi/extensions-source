package eu.kanade.tachiyomi.extension.en.mangasail

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Mangasail : ParsedHttpSource() {

    override val name = "Mangasail"

    override val baseUrl = "https://www.sailmg.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    /* Site loads some manga info (manga cover, author name, status, etc.) client side through JQuery
    need to add this header for when we request these data fragments
    Also necessary for latest updates request */
    override fun headersBuilder() = super.headersBuilder().add("X-Authcache", "1")

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/directory/hot?page=${page - 1}", headers)

    override fun popularMangaSelector() = "tbody tr"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("td:first-of-type a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst("td img")!!.attr("src")
    }

    override fun popularMangaNextPageSelector() = "table + div.text-center ul.pagination li.next a"

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "ul#latest-list > li"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.select("a strong").text()
        element.selectFirst("a:has(img)")!!.let {
            url = it.attr("href")
            // Thumbnails are kind of low-res on latest updates page, transform the img url to get a better version
            thumbnail_url = it.selectFirst("img")
                ?.attr("src")
                ?.substringBefore("?")
                ?.replace("styles/minicover/public/", "")
        }
    }

    override fun latestUpdatesNextPageSelector(): String = "There is no next page"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val genreFilter = filterList.find { it is GenreFilter } as GenreFilter

        return when {
            query.isNotBlank() -> GET("$baseUrl/search/node/$query?page=${page - 1}")
            genreFilter.state != 0 -> GET("$baseUrl/tags/${genreFilter.toUriPart()}?page=${page - 1}")
            else -> GET("$baseUrl/directory/hot?page=${page - 1}", headers)
        }
    }

    override fun searchMangaSelector() = "h3.title, div.view-content div.views-row"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst(".views-field-title a")
            ?: element.selectFirst("a")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        title = anchor!!.text()
        setUrlWithoutDomain(anchor.absUrl("href"))
    }

    override fun searchMangaNextPageSelector() = "li.next a"

    private val json: Json by injectLazy()

    // On source's website most of these details are loaded through JQuery
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        with(document.selectFirst("[id*=node-].node-manga[about]")!!) {
            author = selectByText("Author")?.text()
            artist = selectByText("Artist")?.text()
            genre = selectByText("genres")?.select("a[href*=/tags]")
                ?.joinToString { it.text() }
            status = selectByText("Status")?.text().toStatus()
            description = selectFirst(".field-type-text-with-summary p")?.text()
            thumbnail_url = selectFirst("img")?.absUrl("src")
        }
    }

    private fun Element.selectByText(key: String): Element? =
        selectFirst(".field-label:contains($key) + .field-items .field-item")

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing") -> SManga.ONGOING
        this.contains("Complete") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "tbody tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
        name = element.select("a").text()
        date_upload = parseChapterDate(element.select("td + td").text())
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var currentPage = 0
        val chapters = mutableListOf<SChapter>()
        do {
            val url = "$baseUrl${manga.url}".toHttpUrl().newBuilder()
                .addQueryParameter("page", "${currentPage++}")
                .build()
            val document = client.newCall(GET(url, headers)).execute().asJsoup()
            chapters += document.select(chapterListSelector()).map(::chapterFromElement)
        } while (document.selectFirst(".pagination .pager-last") != null)
        return Observable.just(chapters)
    }

    private fun parseChapterDate(string: String): Long {
        return dateFormat.parse(string.substringAfter("on "))?.time ?: 0L
    }

    // Page List

    override fun pageListParse(document: Document): List<Page> {
        val imgUrlArray = document.selectFirst("script:containsData(paths)")!!.data()
            .substringAfter("paths\":").substringBefore(",\"count_p")
        return json.parseToJsonElement(imgUrlArray).jsonArray
            .map { it.jsonPrimitive.content }
            .filter(URL_REGEX::matches)
            .mapIndexed { i, imageUrl ->
                Page(i, "", imageUrl)
            }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Text search ignores filters"),
        GenreFilter(),
    )

    // From https://www.sailmg.com/tagclouds/chunk/1
    private class GenreFilter : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("<select>", ""),
            Pair("4-Koma", "4-koma"),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Bender", "bender"),
            Pair("Comedy", "comedy"),
            Pair("Cooking", "cooking"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Fantsy", "fantsy"),
            Pair("Game", "game"),
            Pair("Gender", "gender"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Manhua", "manhua"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Mystery", "mystery"),
            Pair("One Shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Sci fi", "sci-fi-0"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatura", "supernatura"),
            Pair("Supernatural", "supernatural"),
            Pair("Supernaturaledit", "supernaturaledit"),
            Pair("Tragedy", "tragedy"),
            Pair("Webtoon", "webtoon"),
            Pair("Webtoons", "webtoons"),
            Pair("Yaoi", "yaoi"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)
        val URL_REGEX = """^https?://[^\s/$.?#].[^\s]*$""".toRegex()
    }
}
