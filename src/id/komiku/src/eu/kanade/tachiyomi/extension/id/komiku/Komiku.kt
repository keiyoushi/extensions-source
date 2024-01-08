package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Komiku : ParsedHttpSource() {
    override val name = "Komiku"

    override val baseUrl = "https://komiku.id"

    private val baseUrlData = "https://data.komiku.id"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // popular
    override fun popularMangaSelector() = "div.bge"

    override fun popularMangaRequest(page: Int): Request {
        return if (page == 1) {
            GET("$baseUrl/other/hot/?orderby=meta_value_num", headers)
        } else {
            GET("$baseUrl/other/hot/page/$page/?orderby=meta_value_num", headers)
        }
    }

    private val coverRegex = Regex("""(/Manga-|/Manhua-|/Manhwa-)""")
    private val coverUploadRegex = Regex("""/uploads/\d\d\d\d/\d\d/""")

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.title = element.select("h3").text().trim()
        manga.setUrlWithoutDomain(element.select("a:has(h3)").attr("href"))

        // scraped image doesn't make for a good cover; so try to transform it
        // make it take bad cover instead of null if it contains upload date as those URLs aren't very useful
        if (element.select("img").attr("data-src").contains(coverUploadRegex)) {
            manga.thumbnail_url = element.select("img").attr("data-src")
        } else {
            manga.thumbnail_url = element.select("img").attr("data-src").substringBeforeLast("?").replace(coverRegex, "/Komik-")
        }

        return manga
    }

    override fun popularMangaNextPageSelector() = ".pag-nav a.next"

    // latest
    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return if (page == 1) {
            GET("$baseUrlData/cari/?post_type=manga&s=&orderby=modified", headers)
        } else {
            GET("$baseUrlData/cari/page/$page/?post_type=manga&s=&orderby=modified", headers)
        }
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrlData/page/$page/?post_type=manga".toHttpUrlOrNull()?.newBuilder()!!.addQueryParameter("s", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryNames -> {
                    val category = filter.values[filter.state]
                    url.addQueryParameter("category_name", category.key)
                }
                is OrderBy -> {
                    val order = filter.values[filter.state]
                    url.addQueryParameter("orderby", order.key)
                }
                is GenreList1 -> {
                    val genre = filter.values[filter.state]
                    url.addQueryParameter("genre", genre.key)
                }
                is GenreList2 -> {
                    val genre = filter.values[filter.state]
                    url.addQueryParameter("genre2", genre.key)
                }
                is StatusList -> {
                    val status = filter.values[filter.state]
                    url.addQueryParameter("status", status.key)
                }
                is ProjectList -> {
                    val project = filter.values[filter.state]
                    if (project.key == "project-filter-on") {
                        url = ("$baseUrl/pustaka" + if (page > 1) "/page/$page/" else "" + "?tipe=projek").toHttpUrlOrNull()!!.newBuilder()
                    }
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "a.next"

    private class Category(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Genre(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Order(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Status(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class CategoryNames(categories: Array<Category>) : Filter.Select<Category>("Category", categories, 0)
    private class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Order", orders, 0)
    private class GenreList1(genres: Array<Genre>) : Filter.Select<Genre>("Genre 1", genres, 0)
    private class GenreList2(genres: Array<Genre>) : Filter.Select<Genre>("Genre 2", genres, 0)
    private class StatusList(statuses: Array<Status>) : Filter.Select<Status>("Status", statuses, 0)
    private class ProjectList(project: Array<Status>) : Filter.Select<Status>("Filter Project", project, 0)

    override fun getFilterList() = FilterList(
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreList1(genreList),
        GenreList2(genreList),
        StatusList(statusList),
        Filter.Separator(),
        Filter.Header("NOTE: cant be used with other filter!"),
        Filter.Header("$name Project List page"),
        ProjectList(projectFilter),
    )

    private val projectFilter = arrayOf(
        Status("Show all manga", ""),
        Status("Show only project manga", "project-filter-on"),
    )

    private val categoryNames = arrayOf(
        Category("All", ""),
        Category("Manga", "manga"),
        Category("Manhua", "manhua"),
        Category("Manhwa", "manhwa"),
    )

    private val orderBy = arrayOf(
        Order("Ranking", "meta_value_num"),
        Order("New Title", "date"),
        Order("Updates", "modified"),
        Order("Random", "rand"),
    )

    private val genreList = arrayOf(
        Genre("All", ""),
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Demons", "demons"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magic", "magic"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("One Shot", "one-shot"),
        Genre("Overpower", "overpower"),
        Genre("Parodi", "parodi"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Romance", "romance"),
        Genre("School", "school"),
        Genre("School life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sport", "sport"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Urban", "urban"),
        Genre("Vampire", "vampire"),
        Genre("Webtoons", "webtoons"),
        Genre("Yuri", "yuri"),
    )

    private val statusList = arrayOf(
        Status("All", ""),
        Status("Ongoing", "ongoing"),
        Status("End", "end"),
    )

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("#Sinopsis > p").text().trim()
        author = document.select("table.inftable td:contains(Komikus)+td").text()
        genre = document.select("li[itemprop=genre] > a").joinToString { it.text() }
        status = parseStatus(document.select("table.inftable tr > td:contains(Status) + td").text())
        thumbnail_url = document.select("div.ims > img").attr("abs:src")

        // add series type(manga/manhwa/manhua/other) thinggy to genre
        val seriesTypeSelector = "table.inftable tr:contains(Jenis) a, table.inftable tr:has(a[href*=category\\/]) a, a[href*=category\\/]"
        document.select(seriesTypeSelector).firstOrNull()?.text()?.let {
            if (it.isEmpty().not() && genre!!.contains(it, true).not()) {
                genre += if (genre!!.isEmpty()) it else ", $it"
            }
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListSelector() = "#Daftar_Chapter tr:has(td.judulseries)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").text()

        val timeStamp = element.select("td.tanggalseries")
        date_upload = if (timeStamp.text().contains("lalu")) {
            parseRelativeDate(timeStamp.text().trim())
        } else {
            parseDate(timeStamp.last()!!)
        }
    }

    private fun parseDate(element: Element): Long = SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(element.text())?.time ?: 0

    // Used Google translate here
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringBefore(" lalu").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "jam" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "menit" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "detik" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#Baca_Komik img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")
}
