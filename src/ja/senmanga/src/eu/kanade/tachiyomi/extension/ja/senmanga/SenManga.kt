package eu.kanade.tachiyomi.extension.ja.senmanga

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class SenManga : ParsedHttpSource() {
    override val lang: String = "ja"

    override val supportsLatest = true
    override val name = "Sen Manga"
    override val baseUrl = "https://raw.senmanga.com"

    @SuppressLint("DefaultLocale")
    override val client = network.cloudflareClient.newBuilder().addInterceptor {
        // Intercept any image requests and add a referer to them
        // Enables bandwidth stealing feature
        val request = if (it.request().url.pathSegments.firstOrNull()?.trim()?.lowercase() == "viewer") {
            it.request().newBuilder()
                .addHeader(
                    "Referer",
                    it.request().url.newBuilder()
                        .removePathSegment(0)
                        .toString(),
                )
                .build()
        } else {
            it.request()
        }
        it.proceed(request)
    }.build()

    override fun popularMangaSelector() = ".mng"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("div.series-title").text()
        thumbnail_url = element.select(".cover img").attr("data-src")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination a[rel=next]"

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/directory/popular?page=$page")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.state.forEach { genre ->
                    if (genre.state) {
                        url.addQueryParameter("genre[]", genre.id)
                    }
                }
                is StatusFilter -> url.addQueryParameter("status", filter.toUriPart())
                is TypeFilter -> url.addQueryParameter("type", filter.toUriPart())
                is OrderFilter -> url.addQueryParameter("order", filter.toUriPart())
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1.series").text()

        thumbnail_url = document.select("div.cover img").first()!!.attr("src")

        description = document.select("div.summary").first()!!.text()

        val seriesElement = document.select("div.series-desc .info")

        genre = seriesElement.select(".item.genre a").joinToString(", ") { it.text() }
        status = seriesElement.select(".item:has(strong:contains(Status))").first()?.text().orEmpty().let {
            parseStatus(it.substringAfter("Status:"))
        }
        author = seriesElement.select(".item:has(strong:contains(Author)) a").text()
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Complete") -> SManga.COMPLETED
        status.contains("Hiatus") -> SManga.ON_HIATUS
        status.contains("Dropped") -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/directory/last_update?page=$page", headers)
    }

    override fun chapterListSelector() = "ul.chapter-list li"

    @SuppressLint("DefaultLocale")
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val linkElement = element.getElementsByTag("a")

        setUrlWithoutDomain(linkElement.attr("href"))

        name = linkElement.first()!!.text()

        chapter_number = element.child(0).text().trim().toFloatOrNull() ?: -1f

        date_upload = parseDate(element.select("time").attr("datetime"))
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(date)?.time ?: 0
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageCount = document
            .select("select.page-list option:last-of-type")
            .first()!!
            .attr("value")
            .toInt()
        val firstUrl = document.select("img.picture").first()!!.attr("src")
        return if (firstUrl.contains(baseUrl)) {
            listOf(1..pageCount).flatten().map { i ->
                Page(i - 1, "", "${document.location().replace(baseUrl, "$baseUrl/viewer")}/$i")
            }
        } else {
            // request each page for image url
            listOf(Page(0, "", firstUrl)) + listOf(1 until pageCount).flatten().map { i ->
                val url = "${document.location()}/${i + 1}"
                val doc = client.newCall(GET(url)).execute().asJsoup()
                val imageUrl = doc.select("img.picture").first()!!.attr("src")
                Page(i, "", imageUrl)
            }
        }
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList()),
        StatusFilter(),
        TypeFilter(),
        OrderFilter(),
    )

    private class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("", "All"),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
            Pair("Hiatus", "Hiatus"),
        ),
    )
    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("", "All"),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
        ),
    )
    private class OrderFilter : UriPartFilter(
        "Order",
        arrayOf(
            Pair("", "Default"),
            Pair("A-Z", "A-Z"),
            Pair("Z-A", "Z-A"),
            Pair("Update", "Update"),
            Pair("Added", "Added"),
            Pair("Popular", "Popular"),
            Pair("Rating", "Rating"),
        ),
    )

    private fun getGenreList(): List<Genre> = listOf(
        Genre("Action"),
        Genre("Adult"),
        Genre("Adventure"),
        Genre("Comedy"),
        Genre("Cooking"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Fantasy"),
        Genre("Gender Bender"),
        Genre("Harem"),
        Genre("Historical"),
        Genre("Horror"),
        Genre("Josei"),
        Genre("Light Novel"),
        Genre("Martial Arts"),
        Genre("Mature"),
        Genre("Music"),
        Genre("Mystery"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("School Life"),
        Genre("Sci-Fi"),
        Genre("Seinen"),
        Genre("Shoujo"),
        Genre("Shoujo Ai"),
        Genre("Shounen"),
        Genre("Shounen Ai"),
        Genre("Slice of Life"),
        Genre("Smut"),
        Genre("Sports"),
        Genre("Supernatural"),
        Genre("Tragedy"),
        Genre("Webtoons"),
        Genre("Yaoi"),
        Genre("Yuri"),
    )
}
