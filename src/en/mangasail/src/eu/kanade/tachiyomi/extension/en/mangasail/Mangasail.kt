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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup.parse
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Mangasail : ParsedHttpSource() {

    override val name = "Mangasail"

    override val baseUrl = "https://www.mangasail.co"

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

    override fun latestUpdatesRequest(page: Int) =
        GET(
            "$baseUrl/sites/all/modules/authcache/modules/authcache_p13n/frontcontroller/authcache.php?r=frag/block/showmanga-lastest_list&o[q]=node",
            headers,
        )

    override fun latestUpdatesSelector() = "ul#latest-list > li"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.select("a strong").text()
        element.select("a:has(img)").let {
            url = it.attr("href")
            // Thumbnails are kind of low-res on latest updates page, transform the img url to get a better version
            thumbnail_url = it.select("img").first()!!.attr("src").substringBefore("?").replace("styles/minicover/public/", "")
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

    override fun searchMangaSelector() = "h3.title, div.region-content h2:has(a)"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.selectFirst("a")!!.let {
            manga.setUrlWithoutDomain(it.attr("abs:href"))
            manga.title = it.text()
            // Search page doesn't contain cover images, have to get them from the manga's page; but first we need that page's node number
            val node = getNodeNumber(client.newCall(GET(it.attr("href"), headers)).execute().asJsoup())
            manga.thumbnail_url = getNodeDetail(node, "field_image2")
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = "li.next a"

    private val json: Json by injectLazy()

    // Function to get data fragments from website
    private fun getNodeDetail(node: String, field: String): String? {
        val requestUrl =
            "$baseUrl/sites/all/modules/authcache/modules/authcache_p13n/frontcontroller/authcache.php?a[field][0]=$node:full:en&r=asm/field/node/$field&o[q]=node/$node"
        val responseString = client.newCall(GET(requestUrl, headers)).execute().body.string()
        val htmlString = json.parseToJsonElement(responseString).jsonObject["field"]!!.jsonObject["$node:full:en"]!!.jsonPrimitive.content
        return parse(htmlString).let {
            when (field) {
                "field_image2" -> it.selectFirst("img.img-responsive")!!.attr("src")
                "field_status", "field_author", "field_artist" -> it.selectFirst("div.field-item.even")?.text()
                "body" -> it.selectFirst("div.field-item.even p")?.text()?.substringAfter("summary: ")
                "field_genres" -> it.select("a").text()
                else -> null
            }
        }
    }

    // Get a page's node number so we can get data fragments for that page
    private fun getNodeNumber(document: Document): String =
        document.select("[rel=shortlink]").attr("href").substringAfter("/node/")

    // On source's website most of these details are loaded through JQuery
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("div.main-content-inner").select("h1").first()!!.text()
            getNodeNumber(document).let { node ->
                author = getNodeDetail(node, "field_author")
                artist = getNodeDetail(node, "field_artist")
                genre = getNodeDetail(node, "field_genres")?.replace(" ", ", ")
                status = getNodeDetail(node, "field_status").toStatus()
                description = getNodeDetail(node, "body")
                thumbnail_url = getNodeDetail(node, "field_image2")
            }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing") -> SManga.ONGOING
        this.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "tbody tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
        name = element.select("a").text()
        date_upload = parseChapterDate(element.select("td + td").text())
    }

    private fun parseChapterDate(string: String): Long {
        return dateFormat.parse(string.substringAfter("on "))?.time ?: 0L
    }

    // Page List

    override fun pageListParse(document: Document): List<Page> {
        val imgUrlArray = document.selectFirst("script:containsData(paths)")!!.data()
            .substringAfter("paths\":").substringBefore(",\"count_p")
        return json.parseToJsonElement(imgUrlArray).jsonArray.mapIndexed { i, el ->
            Page(i, "", el.jsonPrimitive.content)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Text search ignores filters"),
        GenreFilter(),
    )

    // From https://www.mangasail.co/tagclouds/chunk/1
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
        val dateFormat by lazy {
            SimpleDateFormat("d MMM yyyy", Locale.US)
        }
    }
}
