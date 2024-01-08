package eu.kanade.tachiyomi.extension.en.comicastle

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale

class Comicastle : ParsedHttpSource() {

    override val name = "Comicastle"

    override val versionId = 2

    override val baseUrl = "https://www.comicastle.org"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private fun pageSegments(page: Int): String = if (page > 1) "/index/${(page - 1) * 42}" else ""

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/library/popular/desc" + pageSegments(page), headers)
    }

    override fun popularMangaSelector() = "div.shadow-sm"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("p").text()
            setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
            thumbnail_url = element.select("img").attr("data-src")
        }
    }

    override fun popularMangaNextPageSelector() = "li.page-item.next a"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/library/postdate/desc" + pageSegments(page), headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library/search".toHttpUrlOrNull()!!.newBuilder()
        var rBody: RequestBody? = null

        (filters.let { if (it.isEmpty()) getFilterList() else filters })
            .filterIsInstance<PostFilter>()
            .firstOrNull { it.hasSelection() }
            ?.let { filter ->
                url.addPathSegment(filter.pathSegment)
                rBody = filter.toRequestBody()
            }

        if (rBody == null) {
            url.addPathSegment("result")
            rBody = createRequestBody(query)
        }

        return POST(url.toString(), headers, rBody!!)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            with(document.select("div.card-body > div.mb-2")) {
                thumbnail_url = select("img").attr("data-src")
                val publisher = select("p:contains(Publisher) + div button")
                    .firstOrNull()?.let { "Publisher: ${it.text()}\n" }
                description = publisher + select("#comic-desc").text()
                author = select("thead:contains(Writer) + tbody button").joinToString { it.text() }
                artist = select("thead:contains(Artist) + tbody button").joinToString { it.text() }
                status = select("p span.mr-1 strong").text().toStatus()
                genre = select("p:contains(Genre) ~ div button").joinToString { it.text() }
            }
        }
    }

    private fun String.toStatus() = when {
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "div.card-body > .table-responsive tr a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.text()
            setUrlWithoutDomain(element.attr("href").replace("pbp", "swiper"))
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".swiper-wrapper .swiper-slide img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Cannot combine search types!"),
        Filter.Separator(),
        PostFilter("Genre", getGenreList()),
        PostFilter("Year", getYearList()),
        PostFilter("Publisher", getPublisherList()),
    )

    private open class PostFilter(name: String, val vals: Array<String>) : Filter.Select<String>(name, vals) {
        val pathSegment = name.lowercase(Locale.US)
        fun hasSelection(): Boolean = state != 0
        fun toRequestBody(): RequestBody = createRequestBody(vals[state])
    }

    private fun getGenreList() = arrayOf("<Select>", "Action/Adventure", "Anthology", "Anthropomorphic", "Biography", "Children's", "Comedy", "Crime", "Drama", "Fantasy", "Gore", "Graphic Novels", "Historical", "Holiday", "Horror", "Leading Ladies", "LGBTQ", "Literature", "Manga", "Martial Arts", "Mature", "Military", "Movies & TV", "Music", "Mystery", "Mythology", "Non-Fiction", "Original Series", "Political", "Post-Apocalyptic", "Pulp", "Religious", "Risque", "Robots, Cyborgs & Mecha", "Romance", "School Life", "Science Fiction", "Slice of Life", "Spy", "Steampunk", "Superhero", "Supernatural/Occult", "Suspense", "Vampires", "Video Games", "Web Comics", "Werewolves", "Western", "Zombies")
    private fun getYearList() = arrayOf("<Select>") + (Calendar.getInstance()[1] downTo 1963).map { it.toString() }.toTypedArray()
    private fun getPublisherList() = arrayOf("<Select>", "Action Lab", "Aftershock", "AHOY", "American Mythology", "Aspen", "Avatar Press", "AWA Studios", "Black Mask", "BOOM! Studios", "Dark Horse", "DC", "Death Rattle", "Dynamite", "IDW", "Image", "Magnetic Press", "Marvel", "MAX", "Titan", "Ubiworkshop", "Valiant", "Vault", "Vertigo", "Wildstorm", "Zenescope")
}

private fun createRequestBody(value: String) =
    ("submit=Submit&search=" + URLEncoder.encode(value, "UTF-8")).toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
