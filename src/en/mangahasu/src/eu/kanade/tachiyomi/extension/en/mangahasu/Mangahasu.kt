package eu.kanade.tachiyomi.extension.en.mangahasu

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Mangahasu : ParsedHttpSource() {

    override val name = "Mangahasu"

    override val baseUrl = "https://mangahasu.se"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/most-popular.html?page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest-releases.html?page=$page", headers)

    // Only selects popular of all time
    override fun popularMangaSelector() = "div.right div.div_item"

    override fun latestUpdatesSelector() = "div.div_item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first()!!.attr("src")
        element.select("a:has(h3.name-manga), a.name-manga").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a[title = Tiếp]"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/advanced-search.html".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("keyword", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> url.addQueryParameter("author", filter.state)
                is ArtistFilter -> url.addQueryParameter("artist", filter.state)
                is StatusFilter -> url.addQueryParameter("status", filter.toUriPart())
                is TypeFilter -> url.addQueryParameter("typeid", filter.toUriPart())
                is GenreFilter -> {
                    filter.state.forEach {
                        when (it.state) {
                            Filter.TriState.STATE_INCLUDE -> url.addQueryParameter("g_i[]", it.id)
                            Filter.TriState.STATE_EXCLUDE -> url.addQueryParameter("g_e[]", it.id)
                        }
                    }
                }
                is OrderByFilter -> {
                    filter.state?.let {
                        var sortId = it.index

                        // Increment for consistency
                        when (sortId) {
                            1 -> sortId += 1
                            2 -> sortId += 2
                        }

                        val value = if (it.ascending) "${(sortId + 1)}" else "$sortId"

                        url.addQueryParameter("orderby", value)
                    }
                }
                else -> {
                    // ignore
                }
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    // max 200 results
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".info-c").first()!!

        val manga = SManga.create()
        manga.author = isUpdating(infoElement.select(".info")[0].text())
        manga.artist = isUpdating(infoElement.select(".info")[1].text())
        manga.genre = isUpdating(infoElement.select(".info")[3].text())
        manga.status = parseStatus(infoElement.select(".info")[4].text())
        manga.description =
            document.select("div.content-info:has(h3:contains(summary)) div").first()?.text()
        manga.thumbnail_url = document.select("div.info-img img").attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Ongoing") -> SManga.ONGOING
        element.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun isUpdating(string: String): String {
        return if (string == "Updating...") "" else string
    }

    override fun chapterListSelector() = "tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        urlElement.select("span.name-manga").remove()
        chapter.name = urlElement.text()

        chapter.date_upload = element.select(".date-updated").last()?.text()?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(it)?.time ?: 0
        } ?: 0
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        // Grab All Pages from site
        // Some images are place holders on new chapters.

        val pageList = document.select("div.img img")
            .mapIndexed { _, el ->
                val pageNumber = el.attr("class").substringAfter("page").toInt()
                Page(pageNumber, "", el.attr("src"))
            }
            .toMutableList()

        // Some images are not yet loaded onto Mangahasu's image server.
        // Decode temporary URLs and replace placeholder images.

        val lstDUrls = document.select("script:containsData(lstDUrls)")
            .html()
            .substringAfter("lstDUrls")
            .substringAfter("\"")
            .substringBefore("\"")

        // Base64 = [] or empty file
        // Ensures lstDUrls exists, otherwise errors will occur
        if (lstDUrls.isNotEmpty() && lstDUrls != "W10=") {
            val jsonRaw = String(Base64.decode(lstDUrls, Base64.DEFAULT))
            val jsonArr = json.parseToJsonElement(jsonRaw).jsonArray

            jsonArr.forEach { jsonEl ->
                val jsonObj = jsonEl.jsonObject
                val pageNumber = jsonObj["page"]!!.jsonPrimitive.int

                pageList.removeAll { page -> page.index == pageNumber }
                pageList.add(Page(pageNumber, "", jsonObj["url"]!!.jsonPrimitive.content))
            }
        }

        return pageList.sortedBy { page -> page.index }
    }

    override fun imageUrlParse(document: Document) = ""

    // Filters

    override fun getFilterList() = FilterList(
        AuthorFilter(),
        ArtistFilter(),
        OrderByFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(getGenreList()),
    )

    private class AuthorFilter : Filter.Text("Author")

    private class ArtistFilter : Filter.Text("Artist")

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("Any", ""),
            Pair("Manga", "10"),
            Pair("Manhwa", "12"),
            Pair("Manhua", "19"),
        ),
    )

    private class OrderByFilter : Filter.Sort(
        "Order By",
        arrayOf(
            "Updated",
            "Views",
            "Subscribers",
        ),
        Selection(0, false),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("Any", ""),
            Pair("Completed", "1"),
            Pair("Ongoing", "2"),
        ),
    )

    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private fun getGenreList() = listOf(
        Genre("4-koma", "46"),
        Genre("Action", "1"),
        Genre("Adaptation", "101"),
        Genre("Adult", "2"),
        Genre("Adventure", "3"),
        Genre("Aliens", "103"),
        Genre("Animals", "73"),
        Genre("Anime", "57"),
        Genre("Anthology", "99"),
        Genre("Award Winning", "48"),
        Genre("Bara", "60"),
        Genre("Comedy", "4"),
        Genre("Comic", "5"),
        Genre("Cooking", "6"),
        Genre("Crime", "92"),
        Genre("Crossdressing", "86"),
        Genre("Delinquents", "83"),
        Genre("Demons", "51"),
        Genre("Doujinshi", "7"),
        Genre("Drama", "8"),
        Genre("Ecchi", "9"),
        Genre("Fan Colored", "107"),
        Genre("Fantasy", "10"),
        Genre("Full Color", "95"),
        Genre("Game", "68"),
        Genre("Gender Bender", "11"),
        Genre("Genderswap", "81"),
        Genre("Ghosts", "90"),
        Genre("Gore", "100"),
        Genre("Gyaru", "97"),
        Genre("Harem", "12"),
        Genre("Historical", "13"),
        Genre("Horror", "14"),
        Genre("Incest", "84"),
        Genre("Isekai", "67"),
        Genre("Josei", "15"),
        Genre("Live Action", "59"),
        Genre("Loli", "91"),
        Genre("Lolicon", "16"),
        Genre("Long Strip", "93"),
        Genre("Mafia", "113"),
        Genre("Magic", "55"),
        Genre("Magical Girls", "89"),
        Genre("Manga Reviews", "64"),
        Genre("Martial Arts", "20"),
        Genre("Mature", "21"),
        Genre("Mecha", "22"),
        Genre("Medical", "23"),
        Genre("Military", "62"),
        Genre("Monster Girls", "87"),
        Genre("Monsters", "72"),
        Genre("Music", "24"),
        Genre("Mystery", "25"),
        Genre("Ninja", "112"),
        Genre("Office Workers", "80"),
        Genre("Official Colored", "96"),
        Genre("One shot", "26"),
        Genre("Others", "114"),
        Genre("Philosophical", "110"),
        Genre("Police", "105"),
        Genre("Post-Apocalyptic", "76"),
        Genre("Psychological", "27"),
        Genre("Reincarnation", "74"),
        Genre("Reverse harem", "69"),
        Genre("Romance", "28"),
        Genre("Samurai", "108"),
        Genre("School Life", "29"),
        Genre("Sci-fi", "30"),
        Genre("Seinen", "31"),
        Genre("Seinen Supernatural", "66"),
        Genre("Sexual Violence", "98"),
        Genre("Shota", "104"),
        Genre("Shotacon", "32"),
        Genre("Shoujo", "33"),
        Genre("Shoujo Ai", "34"),
        Genre("Shoujoai", "63"),
        Genre("Shounen", "35"),
        Genre("Shounen Ai", "36"),
        Genre("Shounenai", "61"),
        Genre("Slice of Life", "37"),
        Genre("Smut", "38"),
        Genre("Sports", "39"),
        Genre("Super power", "70"),
        Genre("Superhero", "88"),
        Genre("Supernatural", "40"),
        Genre("Survival", "77"),
        Genre("Thriller", "75"),
        Genre("Time Travel", "78"),
        Genre("Traditional Games", "111"),
        Genre("Tragedy", "41"),
        Genre("Uncategorized", "65"),
        Genre("User Created", "102"),
        Genre("Vampire", "58"),
        Genre("Vampires", "82"),
        Genre("Video Games", "85"),
        Genre("Virtual Reality", "109"),
        Genre("Web Comic", "94"),
        Genre("Webtoon", "42"),
        Genre("Webtoons", "56"),
        Genre("Wuxia", "71"),
        Genre("Yaoi", "43"),
        Genre("Youkai", "106"),
        Genre("Yuri", "44"),
        Genre("Zombies", "79"),
    )
}
