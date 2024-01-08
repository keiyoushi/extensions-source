package eu.kanade.tachiyomi.extension.en.hiveworks

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class Hiveworks : ParsedHttpSource() {

    // Info

    override val name = "Hiveworks Comics"
    override val baseUrl = "https://hiveworkscomics.com"
    override val lang = "en"
    override val supportsLatest = true

    // Client

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaSelector() = "div.comicblock"
    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).filterNot {
            val url = it.select("a.comiclink").first()!!.attr("abs:href")
            url.contains("sparklermonthly.com") || url.contains("explosm.net") // Filter Unsupported Comics
        }.map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val day = SimpleDateFormat("EEEE", Locale.US).format(Date()).lowercase(Locale.US)
        return GET("$baseUrl/home/update-day/$day", headers)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    // Source's website doesn't appear to have a search function; so searching locally

    private lateinit var searchQuery: String

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
        if (filters.isNotEmpty()) uri.appendPath("home")
        // Append uri filters
        filters.forEach { filter ->
            when (filter) {
                is UriFilter -> filter.addToUri(uri)
                is OriginalsFilter -> if (filter.state) return GET("$baseUrl/originals", headers)
                is KidsFilter -> if (filter.state) return GET("$baseUrl/kids", headers)
                is CompletedFilter -> if (filter.state) return GET("$baseUrl/completed", headers)
                is HiatusFilter -> if (filter.state) return GET("$baseUrl/hiatus", headers)
                else -> { /*Do nothing*/ }
            }
        }
        if (query.isNotEmpty()) {
            searchQuery = query
            uri.fragment("localSearch")
        }
        return GET(uri.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector() + ", div.originalsblock"
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()
        val document = response.asJsoup()

        val selectManga = document.select(searchMangaSelector()).toList()
        val mangas = when {
            url.endsWith("localSearch") -> {
                selectManga.filter { it.text().contains(searchQuery, true) }.map { element -> searchMangaFromElement(element) }
            }
            url.contains("originals") -> {
                selectManga.map { element -> searchOriginalMangaFromElement(element) }
            }
            else -> {
                selectManga.map { element -> searchMangaFromElement(element) }
            }
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)
    private fun searchOriginalMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img")[1].attr("abs:src")
        title = element.select("div.header").text().substringBefore("by").trim()
        author = element.select("div.header").text().substringAfter("by").trim()
        artist = author
        description = element.select("div.description").text().trim()
        url = element.select("a").first()!!.attr("href")
    }

    // Common

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a.comiclink").first()!!.attr("abs:href")
        manga.title = element.select("h1").text().trim()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        manga.artist = element.select("h2").text().removePrefix("by").trim()
        manga.author = manga.artist
        manga.description = element.select("div.description").text().trim()
        manga.genre = element.select("div.comicrating").text().trim()
        return manga
    }

    // Details
    // Fetches details by calling home page again and using the existing url to find the correct comic

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val url = manga.url
        return client.newCall(GET(baseUrl, headers)) // Bypasses mangaDetailsRequest
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response, url).apply { initialized = true }
            }
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url, headers) // Used to open proper page in webview
    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not Used")
    private fun mangaDetailsParse(response: Response, url: String): SManga {
        val document = response.asJsoup()
        return document.select(popularMangaSelector())
            .firstOrNull { url == it.select("a.comiclink").first()!!.attr("abs:href") }
            ?.let { mangaFromElement(it) } ?: SManga.create()
    }

    // Chapters

    // Included to call custom error codes
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            client.newCall(chapterListRequest(manga))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response)
                }
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    override fun chapterListSelector() = "select[name=comic] option"
    override fun chapterListRequest(manga: SManga): Request {
        val uri = Uri.parse(manga.url).buildUpon()
        when {
            "sssscomic" in uri.toString() -> uri.appendQueryParameter("id", "archive") // sssscomic uses query string in url
            "awkwardzombie" in uri.toString() -> uri.appendPath("awkward-zombie").appendPath("archive")
            else -> {
                uri.appendPath("comic")
                uri.appendPath("archive")
            }
        }
        return GET(uri.toString(), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url.toString()
        when {
            "witchycomic" in url -> return witchyChapterListParse(response)
            "sssscomic" in url -> return ssssChapterListParse(response)
            "awkwardzombie" in url -> return awkwardzombieChapterListParse(response)
        }
        val document = response.asJsoup()
        val baseUrl = document.select("div script").html().substringAfter("href='").substringBefore("'")
        val elements = document.select(chapterListSelector())
        if (elements.isNullOrEmpty()) throw Exception("This comic has a unsupported chapter list")
        val chapters = mutableListOf<SChapter>()
        for (i in 1 until elements.size) {
            chapters.add(createChapter(elements[i], baseUrl))
        }
        when {
            "checkpleasecomic" in url -> chapters.retainAll { it.name.endsWith("01") || it.name.endsWith(" 1") }
        }
        chapters.reverse()
        return chapters
    }

    private fun createChapter(element: Element, baseUrl: String?) = SChapter.create().apply {
        name = element.text().substringAfter("-").trim()
        url = baseUrl + element.attr("value")
        date_upload = parseDate(element.text().substringBefore("-").trim(), DATE_FORMATTER)
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not Used")

    // Pages

    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)
    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url.toString()
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        document.select("div#cc-comicbody img").forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        // Site specific pages can be added here
        when {
            "smbc-comics" in url -> {
                pages.add(Page(pages.size, "", document.select("div#aftercomic img").attr("src")))
                pages.add(Page(pages.size, "", smbcTextHandler(document)))
            }
            "sssscomic" in url -> {
                val urlPath = document.select("img.comicnormal").attr("src")
                val urlimg = response.request.url.resolve("../../$urlPath").toString()
                pages.add(Page(pages.size, "", urlimg))
            }
            else -> { /*Do Nothing*/ }
        }

        return pages
    }

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used, see pageListParse(response)")
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Only one filter can be used at a time"),
        Filter.Separator(),
        UpdateDay(),
        RatingFilter(),
        GenreFilter(),
        TitleFilter(),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("Extra Lists"),
        OriginalsFilter(),
        KidsFilter(),
        CompletedFilter(),
        HiatusFilter(),
    )

    private class OriginalsFilter : Filter.CheckBox("Original Comics")
    private class KidsFilter : Filter.CheckBox("Kids Comics")
    private class CompletedFilter : Filter.CheckBox("Completed Comics")
    private class HiatusFilter : Filter.CheckBox("On Hiatus Comics")

    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified) {
                uri.appendPath(uriParam)
                    .appendPath(vals[state].first)
            }
        }
    }

    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    private class UpdateDay : UriSelectFilter(
        "Update Day",
        "update-day",
        arrayOf(
            Pair("all", "All"),
            Pair("monday", "Monday"),
            Pair("tuesday", "Tuesday"),
            Pair("wednesday", "Wednesday"),
            Pair("thursday", "Thursday"),
            Pair("friday", "Friday"),
            Pair("saturday", "Saturday"),
            Pair("sunday", "Sunday"),
        ),
    )

    private class RatingFilter : UriSelectFilter(
        "Rating",
        "age",
        arrayOf(
            Pair("all", "All"),
            Pair("everyone", "Everyone"),
            Pair("teen", "Teen"),
            Pair("young-adult", "Young Adult"),
            Pair("mature", "Mature"),
        ),
    )

    private class GenreFilter : UriSelectFilter(
        "Genre",
        "genre",
        arrayOf(
            Pair("all", "All"),
            Pair("action/adventure", "Action/Adventure"),
            Pair("animated", "Animated"),
            Pair("autobio", "Autobio"),
            Pair("comedy", "Comedy"),
            Pair("drama", "Drama"),
            Pair("dystopian", "Dystopian"),
            Pair("fairytale", "Fairytale"),
            Pair("fantasy", "Fantasy"),
            Pair("finished", "Finished"),
            Pair("historical-fiction", "Historical Fiction"),
            Pair("horror", "Horror"),
            Pair("lgbt", "LGBT"),
            Pair("mystery", "Mystery"),
            Pair("romance", "Romance"),
            Pair("sci-fi", "Science Fiction"),
            Pair("slice-of-life", "Slice of Life"),
            Pair("steampunk", "Steampunk"),
            Pair("superhero", "Superhero"),
            Pair("urban-fantasy", "Urban Fantasy"),
        ),
    )

    private class TitleFilter : UriSelectFilter(
        "Title",
        "alpha",
        arrayOf(
            Pair("all", "All"),
            Pair("a", "A"),
            Pair("b", "B"),
            Pair("c", "C"),
            Pair("d", "D"),
            Pair("e", "E"),
            Pair("f", "F"),
            Pair("g", "G"),
            Pair("h", "H"),
            Pair("i", "I"),
            Pair("j", "J"),
            Pair("k", "K"),
            Pair("l", "L"),
            Pair("m", "M"),
            Pair("n", "N"),
            Pair("o", "O"),
            Pair("p", "P"),
            Pair("q", "Q"),
            Pair("r", "R"),
            Pair("s", "S"),
            Pair("t", "T"),
            Pair("u", "U"),
            Pair("v", "V"),
            Pair("w", "W"),
            Pair("x", "X"),
            Pair("y", "Y"),
            Pair("z", "Z"),
            Pair("numbers-symbols", "Numbers / Symbols"),
        ),
    )

    private class SortFilter : UriSelectFilter(
        "Sort By",
        "sortby",
        arrayOf(
            Pair("none", "None"),
            Pair("a-z", "A-Z"),
            Pair("z-a", "Z-A"),
        ),
    )

    // Other Code

    private fun awkwardzombieChapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        response.asJsoup().select("div.archive-line").forEach {
            chapters.add(
                SChapter.create().apply {
                    val chapterNumber = it.select(".archive-date").text().substringAfter("#").substringBefore(",")
                    chapter_number = chapterNumber.toFloat()
                    name = "#$chapterNumber ${it.select("div.archive-title").text()} (${it.select(".archive-game").text()})"
                    url = it.select("a").attr("abs:href")
                    date_upload = parseDate(it.select(".archive-date").text().substringAfter(", "), awkwardzombieDateFormat)
                },
            )
        }
        return chapters
    }

    // Gets the chapter list for witchycomic
    private fun witchyChapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select(".cc-storyline-pagethumb a")
        if (elements.isNullOrEmpty()) throw Exception("This comic has a unsupported chapter list")
        val chapters = mutableListOf<SChapter>()
        for (i in 1 until elements.size) {
            val chapter = SChapter.create()
            chapter.name = "Page " + i
            chapter.url = elements[i].attr("href")
            // Date upload isn't available for witchy, unfortunately. As a
            // workaround to ensure notifications work, use system time.
            chapter.date_upload = System.currentTimeMillis()
            chapters.add(chapter)
        }
        chapters.retainAll { it.url.contains("page-") }
        chapters.reverse()
        return chapters
    }

    /**
     * Gets the chapter list for sssscomic - based on work by roblabla for witchycomic
     *
     */
    private fun ssssChapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        // Gets the adventure div's
        val advDiv = document.select("div[id^=adv]")
        val chapters = mutableListOf<SChapter>()
        // Iterate through the Div's
        for (i in 1 until advDiv.size + 1) {
            val elements = document.select("#adv${i}Div a")
            if (elements.isNullOrEmpty()) throw Exception("This comic has a unsupported chapter list")
            for (c in 0 until elements.size) {
                val chapter = SChapter.create()
                // Adventure No. and Page No. for chapter name
                chapter.name = "Adventure $i - Page ${elements[c].text()}"
                // Uses relative paths so need to combine the initial host with the path
                val urlPath = elements[c].attr("href")
                chapter.url = response.request.url.resolve("../../$urlPath").toString()
                // use system time as the date of the chapters are per page and takes to long to pull each one.
                chapter.date_upload = System.currentTimeMillis()
                chapters.add(chapter)
            }
        }
        chapters.retainAll { it.url.contains("page") }
        chapters.reverse()
        return chapters
    }

    // Builds Image from mouse tooltip text
    private fun smbcTextHandler(document: Document): String {
        val title = document.select("title").text().trim()
        val altText = document.select("div#cc-comicbody img").attr("title")

        val titleWords: Sequence<String> = title.splitToSequence(" ")
        val altTextWords: Sequence<String> = altText.splitToSequence(" ")

        val builder = StringBuilder()
        var count = 0

        for (i in titleWords) {
            if (count != 0 && count.rem(7) == 0) {
                builder.append("%0A")
            }
            builder.append(i).append("+")
            count++
        }
        builder.append("%0A%0A")

        var charCount = 0

        for (i in altTextWords) {
            if (charCount > 25) {
                builder.append("%0A")
                charCount = 0
            }
            builder.append(i).append("+")
            charCount += i.length + 1
        }

        return "https://fakeimg.pl/1500x2126/ffffff/000000/?text=$builder&font_size=42&font=museo"
    }

    // Used to throw custom error codes for http codes
    private fun Call.asObservableSuccess(): Observable<Response> {
        return asObservable().doOnNext { response ->
            if (!response.isSuccessful) {
                response.close()
                when (response.code) {
                    404 -> throw Exception("This comic has a unsupported chapter list")
                    else -> throw Exception("HiveWorks Comics HTTP Error ${response.code}")
                }
            }
        }
    }

    private fun parseDate(dateStr: String, format: SimpleDateFormat): Long {
        return runCatching { format.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
        private val awkwardzombieDateFormat by lazy { SimpleDateFormat("MM-dd-yy", Locale.US) }
    }
}
