package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Komiku : ParsedHttpSource() {
    override val name = "Komiku"

    override val baseUrl = "https://komiku.org"

    private val baseUrlApi = "https://api.komiku.org"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // ============================== Popular ===============================
    override fun popularMangaSelector() = "div.bge"

    override fun popularMangaRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrlApi/other/hot/?orderby=meta_value_num", headers)
    } else {
        GET("$baseUrlApi/other/hot/page/$page/?orderby=meta_value_num", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.title = element.selectFirst("h3")!!.text()
        manga.setUrlWithoutDomain(element.selectFirst("a:has(h3)")!!.attr("href"))

        // scraped image doesn't make for a good cover; so try to transform it
        // make it take bad cover instead of null if it contains upload date as those URLs aren't very useful
        if (element.select("img").attr("abs:src").contains(coverUploadRegex)) {
            manga.thumbnail_url = element.select("img").attr("abs:src")
        } else {
            manga.thumbnail_url = element.select("img").attr("abs:src").substringBeforeLast("?").replace(coverRegex, "/Komik-")
        }

        return manga
    }

    override fun popularMangaNextPageSelector() = "span[hx-get]"

    // =============================== Latest ===============================
    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrlApi/other/hot/?orderby=modified", headers)
    } else {
        GET("$baseUrlApi/other/hot/page/$page/?orderby=modified", headers)
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrlApi.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            if (page > 1) {
                addPathSegments("page/$page")
            }

            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            }

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is CategoryNames -> addQueryParameter("tipe", filter.values[filter.state].key)
                    is OrderBy -> addQueryParameter("orderby", filter.values[filter.state].key)
                    is GenreParameter -> addQueryParameter("genre", filter.values[filter.state].key)
                    is Genre2Parameter -> addQueryParameter("genre2", filter.values[filter.state].key)
                    is StatusParameter -> addQueryParameter("status", filter.values[filter.state].key)
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.code == 404) return MangasPage(emptyList(), false)
        val mangasPage = super.searchMangaParse(response)
        // The URL supports pagination, but the source UI does not display pagination buttons.
        return MangasPage(mangasPage.mangas, mangasPage.hasNextPage || mangasPage.mangas.size >= 10)
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("#Sinopsis > p").text()

        document.select("table.inftable tr:contains(Judul Indonesia) td + td").text().let {
            if (it.isNotEmpty()) {
                description = (if (description.isNullOrEmpty()) "" else "$description\n\n") + "Judul Indonesia: $it"
            }
        }

        author = document.select("table.inftable td:contains(Pengarang)+td, table.inftable td:contains(Komikus)+td").text().takeIf { it.isNotEmpty() }
        genre = document.select("ul.genre li.genre a span").joinToString { it.text() }.takeIf { it.isNotEmpty() }
        status = parseStatus(document.select("table.inftable tr > td:contains(Status) + td").text())
        thumbnail_url = document.selectFirst("div.ims > img")?.attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing", true) || status.contains("On Going", true) -> SManga.ONGOING
        status.contains("End", true) || status.contains("Completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ===============================
    override fun chapterListSelector() = "#Daftar_Chapter tr:has(td.judulseries)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name = element.selectFirst("a")!!.text()

        val timeStamp = element.select("td.tanggalseries")
        date_upload = if (timeStamp.text().contains("lalu")) {
            parseRelativeDate(timeStamp.text())
        } else {
            dateFormat.tryParse(timeStamp.last()?.text())
        }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    // Used Google translate here
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringBefore(" lalu").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "jam" -> calendar.add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt())
            "menit" -> calendar.add(Calendar.MINUTE, -trimmedDate[0].toInt())
        }

        return calendar.timeInMillis
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> = document.select("#Baca_Komik img").mapIndexed { i, element ->
        Page(i, "", element.attr("abs:src"))
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    private class Category(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String = name
    }

    private class Genre(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String = name
    }

    private class Order(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String = name
    }

    private class Status(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String = name
    }

    private class CategoryNames(categories: Array<Category>) : Filter.Select<Category>("Tipe", categories, 0)
    private class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Order", orders, 0)
    private class GenreParameter(genres: Array<Genre>) : Filter.Select<Genre>("Genre 1", genres, 0)
    private class Genre2Parameter(genres: Array<Genre>) : Filter.Select<Genre>("Genre 2", genres, 0)
    private class StatusParameter(statuses: Array<Status>) : Filter.Select<Status>("Status", statuses, 0)

    override fun getFilterList() = FilterList(
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreParameter(genreList),
        Genre2Parameter(genreList),
        StatusParameter(statusList),
    )

    private val categoryNames = arrayOf(
        Category("Semua", ""),
        Category("Manga", "manga"),
        Category("Manhua", "manhua"),
        Category("Manhwa", "manhwa"),
    )

    private val orderBy = arrayOf(
        Order("Chapter Terbaru", "modified"),
        Order("Komik Terbaru", "date"),
        Order("Peringkat", "meta_value_num"),
        Order("Acak", "rand"),
    )

    private val genreList = arrayOf(
        Genre("Semua", ""),
        Genre("Academy", "academy"),
        Genre("Action", "action"),
        Genre("Adaptation", "adaptation"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("apocalypse", "apocalypse"),
        Genre("Beasts", "beasts"),
        Genre("Blacksmith", "blacksmith"),
        Genre("Comedy", "comedy"),
        Genre("Comic", "comic"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Crossdressing", "crossdressing"),
        Genre("Dark Fantasy", "dark-fantasy"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Entertainment", "entertainment"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Genderswap", "genderswap"),
        Genre("Genius", "genius"),
        Genre("Ghosts", "ghosts"),
        Genre("Gore", "gore"),
        Genre("Gyaru", "gyaru"),
        Genre("Harem", "harem"),
        Genre("Hentai", "hentai"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Knight", "knight"),
        Genre("Long Strip", "long-strip"),
        Genre("Magic", "magic"),
        Genre("Magical Girls", "magical-girls"),
        Genre("Manga", "manga"),
        Genre("Mangatoon", "mangatoon"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Art", "martial-art"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("MC Rebirth", "mc-rebirth"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Monster", "monster"),
        Genre("Monster girls", "monster-girls"),
        Genre("Monsters", "monsters"),
        Genre("Murim", "murim"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("Office Workers", "office-workers"),
        Genre("One Shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Regression", "regression"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Revenge", "revenge"),
        Genre("Romance", "romance"),
        Genre("School", "school"),
        Genre("School life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Sexual Violence", "sexual-violence"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shoujo(G)", "shoujog"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Slow Life", "slow-life"),
        Genre("Smut", "smut"),
        Genre("Sport", "sport"),
        Genre("Sports", "sports"),
        Genre("Strategy", "strategy"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Survival", "survival"),
        Genre("Sword Fight", "sword-fight"),
        Genre("Sword Master", "sword-master"),
        Genre("Swormanship", "swormanship"),
        Genre("System", "system"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Trauma", "trauma"),
        Genre("Vampire", "vampire"),
        Genre("Villainess", "villainess"),
        Genre("Violence", "violence"),
        Genre("Web Comic", "web-comic"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Xianxia", "xianxia"),
        Genre("Xuanhuan", "xuanhuan"),
        Genre("Yuri", "yuri"),
    )

    private val statusList = arrayOf(
        Status("Semua", ""),
        Status("Ongoing", "ongoing"),
        Status("Tamat", "end"),
    )

    companion object {
        private val coverRegex = Regex("""(/Manga-|/Manhua-|/Manhwa-)""")
        private val coverUploadRegex = Regex("""/uploads/\d\d\d\d/\d\d/""")
    }
}
