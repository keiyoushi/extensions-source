package eu.kanade.tachiyomi.extension.en.mangamonks

import eu.kanade.tachiyomi.extension.en.mangamonks.MangaMonksHelper.buildApiHeaders
import eu.kanade.tachiyomi.extension.en.mangamonks.MangaMonksHelper.toDate
import eu.kanade.tachiyomi.extension.en.mangamonks.MangaMonksHelper.toFormRequestBody
import eu.kanade.tachiyomi.extension.en.mangamonks.MangaMonksHelper.toStatus
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class MangaMonks : ParsedHttpSource() {

    override val name = "MangaMonks"

    override val baseUrl = "https://mangamonks.com"

    override val lang = "en"

    override val supportsLatest = true

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular-manga/$page", headers)
    }
    override fun popularMangaSelector() = ".main-slide"
    override fun popularMangaNextPageSelector() = "li:nth-last-child(2) a.page-btn"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.selectFirst(".detail a")!!.text()
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            thumbnail_url = element.select("img").attr("data-src")
        }
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-releases/$page", headers)
    }

    override fun latestUpdatesSelector() = ".tab-pane .row .col-12"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.let { if (it.isEmpty()) getFilterList() else it }
        return if (query.isNotEmpty()) {
            val requestBody = query.toFormRequestBody()
            val requestHeaders = headersBuilder().buildApiHeaders(requestBody)

            POST("$baseUrl/search/live", requestHeaders, requestBody)
        } else {
            val url = "$baseUrl/genre/".toHttpUrl().newBuilder()
            filterList.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart().let {
                        url.apply {
                            addPathSegment(it)
                            addQueryParameter("include[]", filter.toGenreValue())
                        }
                    }
                    is StatusFilter -> filter.toUriPart().let {
                        url.apply {
                            addQueryParameter("term", query)
                            addQueryParameter("status[]", it)
                        }
                    }
                    else -> {}
                }
            }

            url.addPathSegment(page.toString())
            GET(url.build(), headers)
        }
    }

    override fun searchMangaSelector() = ".main-slide .item"
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private val json: Json by injectLazy()
    override fun searchMangaParse(response: Response): MangasPage {
        val isJson = response.header("Content-Type")?.contains("application/json") ?: false
        if (isJson) {
            return try {
                val result = json.decodeFromString<MangaList>(response.body.string())
                val mangaList = result.manga.map {
                    SManga.create().apply {
                        title = it.title
                        setUrlWithoutDomain(it.url)
                        thumbnail_url = it.image
                    }
                }
                val hasNextPage = false
                MangasPage(mangaList, hasNextPage)
            } catch (_: MissingFieldException) {
                MangasPage(emptyList(), false)
            }
        } else {
            val document = response.asJsoup()

            val mangas = document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }

            val hasNextPage = searchMangaNextPageSelector().let { selector ->
                document.select(selector).first()
            } != null

            return MangasPage(mangas, hasNextPage)
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            author = document.selectFirst(".publisher a")!!.text()
            status = document.selectFirst(".info-detail .source")!!.text().toStatus()
            genre = document.select(".info-detail .tags a").joinToString { it.text() }
            description = document.select(".info-desc p").text()
            thumbnail_url = document.select(".img-holder img").attr("data-src")
        }
    }

    // chapters
    override fun chapterListSelector() = ".chapter-list li"
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.select("a").attr("href"))
            name = element.select(".chapter-number").text()
            date_upload = element.select(".time").text().trim().toDate()
        }
    }
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#zoomContainer .image img").mapIndexed { i, it ->
            val src = it.attr("src")
            val imageUrl = if (src.startsWith("https")) src else baseUrl + src
            Page(i, imageUrl = imageUrl)
        }
    }

    // filters
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        StatusFilter(),
        GenreFilter(),
    )
    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )
    private class GenreFilter : GenreValueFilter(
        "Genre",
        arrayOf(
            Triple("Action", "2", "action"),
            Triple("Adventure", "3", "adventure"),
            Triple("Comedy", "5", "comedy"),
            Triple("Cooking", "6", "cooking"),
            Triple("Doujinshi", "7", "doujinshi"),
            Triple("Drama", "8", "drama"),
            Triple("Ecchi", "9", "ecchi"),
            Triple("Yaoi", "11", "yaoi"),
            Triple("Fantasy", "12", "fantasy"),
            Triple("Gender Bender", "13", "gender-bender"),
            Triple("Harem", "14", "harem"),
            Triple("Historical", "15", "historical"),
            Triple("Horror", "16", "horror"),
            Triple("Josei", "17", "josei"),
            Triple("Manhua", "18", "manhua"),
            Triple("Manhwa", "19", "manhwa"),
            Triple("Mecha", "21", "mecha"),
            Triple("Mystery", "24", "mystery"),
            Triple("One Shot", "25", "one-shot"),
            Triple("Psychological", "26", "psychological"),
            Triple("Romance", "27", "romance"),
            Triple("School Life", "28", "school-life"),
            Triple("Sci-fi", "29", "sci-fi"),
            Triple("Seinen", "30", "seinen"),
            Triple("Yuri", "31", "yuri"),
            Triple("Shoujo", "32", "shoujo"),
            Triple("Shounen", "34", "shounen"),
            Triple("Shounen Ai", "35", "shounen-ai"),
            Triple("Slice of Life", "36", "slice-of-life"),
            Triple("Sports", "37", "sports"),
            Triple("Supernatural", "38", "supernatural"),
            Triple("Tragedy", "39", "tragedy"),
            Triple("Webtoons", "40", "webtoons"),
            Triple("Full Color", "42", "full-color"),
            Triple("Isekai", "44", "isekai"),
            Triple("Reincarnation", "45", "reincarnation"),
            Triple("Time Travel", "46", "time-travel"),
            Triple("Martial arts", "48", "martial-arts"),
            Triple("Monsters", "49", "monsters-monsters"),
            Triple("Thriller", "51", "thriller"),
            Triple("Adaptation", "52", "adaptation"),
            Triple("Reverse Harem", "53", "reverse-harem"),
            Triple("Cross-dressing", "54", "cross-dressing"),
            Triple("Zombies", "55", "zombies"),
            Triple("Crime", "56", "crime"),
            Triple("Ghosts", "57", "ghosts"),
            Triple("Magic", "58", "magic"),
            Triple("Gore", "59", "gore"),
            Triple("+18", "84", "18"),
            Triple("LGBT", "47", "lgbt"),
            Triple("erotic", "62", "erotic"),
            Triple("Harem", "63", "harem-harem"),
            Triple("MILF", "64", "milf"),
            Triple("Yaoi/boy's love", "65", "yaoiboys-love"),
            Triple("Yuri/girl's love", "66", "yurigirls-love"),
            Triple("BBW", "67", "bbw"),
            Triple("Shota", "68", "shota"),
            Triple("NTR/cheating", "69", "ntrcheating"),
            Triple("BDSM", "70", "bdsm"),
            Triple("tentacle", "71", "tentacle"),
            Triple("Oyasumi/sleeping", "72", "oyasumisleeping"),
            Triple("Elf Hentai", "74", "elf-hentai"),
            Triple("Rape", "75", "rape"),
            Triple("Incest", "76", "incest"),
            Triple("Inseki", "77", "inseki"),
            Triple("LGBTQ", "78", "lgbtq"),
            Triple("Beastiality", "79", "bestiality"),
            Triple("Defloration", "80", "defloration"),
            Triple("loli", "81", "loli"),
            Triple("Raw", "83", "raw"),
        ),
    )
    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
    private open class GenreValueFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].third
        fun toGenreValue() = vals[state].second
    }

    @Serializable
    class MangaList(val manga: List<MangaItem>)

    @Serializable
    class MangaItem(val title: String, val url: String, val image: String)
}
