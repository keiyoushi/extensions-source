package eu.kanade.tachiyomi.extension.en.oppaistream

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLDecoder
import java.util.Calendar

class OppaiStream : ParsedHttpSource() {

    override val name = "Oppai Stream"

    override val baseUrl = "https://read.oppai.stream"

    private val cdnUrl = "https://myspacecat.pictures"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList(OrderByFilter("views")))
    }

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList(OrderByFilter("uploaded")))
    }

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    // search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SLUG_SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val url = "/manhwa?m=${query.substringAfter(SLUG_SEARCH_PREFIX)}"
        return fetchMangaDetails(SManga.create().apply { this.url = url }).map {
            it.url = url
            MangasPage(listOf(it), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api-search.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("text", query)
            filters.forEach { filter ->
                when (filter) {
                    is OrderByFilter -> {
                        addQueryParameter("order", filter.selectedValue())
                    }
                    is GenreListFilter -> {
                        addQueryParameter("genres", filter.state.filter { it.isIncluded() }.joinToString(",") { it.value })
                        addQueryParameter("blacklist", filter.state.filter { it.isExcluded() }.joinToString(",") { it.value })
                    }
                    else -> {}
                }
            }
            addQueryParameter("page", "$page")
            addQueryParameter("limit", "$SEARCH_LIMIT")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.in-grid > a"

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val elements = document.select(searchMangaSelector())

        val mangas = elements.map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = elements.size >= SEARCH_LIMIT

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            thumbnail_url = element.select("img.read-cover").attr("src")
            title = element.select("h3.man-title").text()
            val rawUrl = element.absUrl("href")
            val url = if (rawUrl.contains("/fw?to=")) {
                URLDecoder.decode(rawUrl.substringAfter("/fw?to="), "UTF-8")
            } else {
                rawUrl
            }
            setUrlWithoutDomain(url)
        }
    }

    override fun searchMangaNextPageSelector() = null

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select(".cover-img").attr("src")
            document.select(".manhwa-info-in").let { it ->
                it.select("h1").run {
                    title = text().substringBeforeLast("By").trim()
                    author = select("a.red").text().trim()
                    artist = author
                }
                genre = it.select(".genres h5").joinToString { it.text() }
                description = it.select(".description").text()
            }
        }
    }

    // chapter list
    override fun chapterListSelector() = ".sort-chapters > a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.select("div > h4").text()
            date_upload = element.select("div > h6").text().parseRelativeDate()
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    // page list
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        val slug = chapterUrl.queryParameter("m")
        val chapNo = chapterUrl.queryParameter("c")

        return GET("$cdnUrl/manhwa/im.php?f-m=$slug&c=$chapNo", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img").mapIndexed { index, img ->
            Page(index = index, imageUrl = img.attr("src"))
        }
    }

    // filters
    open class SelectFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
        vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ) {
        fun selectedValue() = vals[state].second
    }

    private class OrderByFilter(defaultOrder: String? = null) : SelectFilter(
        "Sort By",
        arrayOf(
            Pair("", ""),
            Pair("A-Z", "az"),
            Pair("Z-A", "za"),
            Pair("Recently Released", "recent"),
            Pair("Oldest Releases", "old"),
            Pair("Most Views", "views"),
            Pair("Highest Rated", "rating"),
            Pair("Recently Uploaded", "uploaded"),
        ),
        defaultOrder,
    )

    internal class TriState(name: String, val value: String) : Filter.TriState(name)

    private fun getGenreList(): List<TriState> = listOf(
        TriState("Adventure", "adventure"),
        TriState("Beach", "beach"),
        TriState("Blackmail", "blackmail"),
        TriState("Cheating", "cheating"),
        TriState("Comedy", "comedy"),
        TriState("Cooking", "cooking"),
        TriState("Drama", "drama"),
        TriState("Fantasy", "fantasy"),
        TriState("Harem", "harem"),
        TriState("Historical", "historical"),
        TriState("Horror", "horror"),
        TriState("Incest", "incest"),
        TriState("Mind Break", "mindbreak"),
        TriState("Mind Control", "mindcontrol"),
        TriState("Monster", "monster"),
        TriState("Mystery", "mystery"),
        TriState("NTR", "ntr"),
        TriState("Psychological", "psychological"),
        TriState("Rape", "rape"),
        TriState("Reverse Rape", "reverserape"),
        TriState("Romance", "romance"),
        TriState("School Life", "schoollife"),
        TriState("Sci-fi", "sci-fi"),
        TriState("Secret Relationship", "secretrelationship"),
        TriState("Slice of Life", "sliceoflife"),
        TriState("Smut", "smut"),
        TriState("Sports", "sports"),
        TriState("Supernatural", "supernatural"),
        TriState("Tragedy", "tragedy"),
        TriState("Yaoi", "yaoi"),
        TriState("Yuri", "yuri"),
        TriState("Big Boobs", "bigboobs"),
        TriState("Black Hair", "blackhair"),
        TriState("Blonde Hair", "blondehair"),
        TriState("Blue Hair", "bluehair"),
        TriState("Brown Hair", "brownhair"),
        TriState("Cosplay", "cosplay"),
        TriState("Dark Skin", "darkskin"),
        TriState("Demon", "demon"),
        TriState("Dominant Girl", "dominantgirl"),
        TriState("Elf", "elf"),
        TriState("Futanari", "futanari"),
        TriState("Glasses", "glasses"),
        TriState("Green Hair", "greenhair"),
        TriState("Gyaru", "gyaru"),
        TriState("Inverted Nipples", "invertednipples"),
        TriState("Loli", "loli"),
        TriState("Maid", "maid"),
        TriState("Milf", "milf"),
        TriState("Nekomimi", "nekomimi"),
        TriState("Nurse", "nurse"),
        TriState("Pink Hair", "pinkhair"),
        TriState("Pregnant", "pregnant"),
        TriState("Purple Hair", "purplehair"),
        TriState("Red Hair", "redhair"),
        TriState("School Girl", "schoolgirl"),
        TriState("Short Hair", "shorthair"),
        TriState("Small Boobs", "smallboobs"),
        TriState("Succubus", "succubus"),
        TriState("Swimsuit", "swimsuit"),
        TriState("Teacher", "teacher"),
        TriState("Tsundere", "tsundere"),
        TriState("Vampire", "vampire"),
        TriState("Virgin", "virgin"),
        TriState("White Hair", "whitehair"),
        TriState("Old", "old"),
        TriState("Shota", "shota"),
        TriState("Trap", "trap"),
        TriState("Ugly Bastard", "uglybastard"),
    )

    private class GenreListFilter(genres: List<OppaiStream.TriState>) : Filter.Group<TriState>("Genre", genres)

    override fun getFilterList() = FilterList(
        OrderByFilter(),
        GenreListFilter(getGenreList()),
    )

    // Unused
    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    // helpers
    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var parsedDate = 0L

        val relativeDate = try {
            this.split(" ")[0].trim().toInt()
        } catch (e: NumberFormatException) {
            return 0L
        }

        when {
            // parse: 30 seconds ago
            "second" in this -> {
                parsedDate = now.apply { add(Calendar.SECOND, -relativeDate) }.timeInMillis
            }
            // parses: "42 minutes ago"
            "minute" in this -> {
                parsedDate = now.apply { add(Calendar.MINUTE, -relativeDate) }.timeInMillis
            }
            // parses: "1 hour ago" and "2 hours ago"
            "hour" in this -> {
                parsedDate = now.apply { add(Calendar.HOUR, -relativeDate) }.timeInMillis
            }
            // parses: "2 days ago"
            "day" in this -> {
                parsedDate = now.apply { add(Calendar.DAY_OF_YEAR, -relativeDate) }.timeInMillis
            }
            // parses: "2 weeks ago"
            "week" in this -> {
                parsedDate = now.apply { add(Calendar.WEEK_OF_YEAR, -relativeDate) }.timeInMillis
            }
            // parses: "2 months ago"
            "month" in this -> {
                parsedDate = now.apply { add(Calendar.MONTH, -relativeDate) }.timeInMillis
            }
            // parse: "2 years ago"
            "year" in this -> {
                parsedDate = now.apply { add(Calendar.YEAR, -relativeDate) }.timeInMillis
            }
        }
        return parsedDate
    }

    companion object {
        const val SEARCH_LIMIT = 36
        const val SLUG_SEARCH_PREFIX = "slug:"
    }
}
