package eu.kanade.tachiyomi.extension.en.manhwalike

import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.buildApiHeaders
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toDate
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toFormRequestBody
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toOriginal
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toStatus
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Manhwalike : ParsedHttpSource() {
    override val name = "Manhwalike"

    override val baseUrl = "https://manhwalike.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector() = "ul.list-hot div.visual"

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.selectFirst("h3.title a")?.text()?.also { title = it }
            element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
            thumbnail_url = element.selectFirst("img")?.toOriginal()
        }
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector() = "ul.slick_item div.visual"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            val requestBody = query.toFormRequestBody()
            val requestHeaders = headersBuilder().buildApiHeaders(requestBody)
            POST("$baseUrl/search/html/1", requestHeaders, requestBody)
        } else {
            val url = baseUrl.toHttpUrl().newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart().also { url.addPathSegment(it) }
                    else -> {}
                }
            }
            url.addQueryParameter("page", page.toString())
            return GET(url.build(), headers)
        }
    }

    override fun searchMangaSelector() = "ul.normal li"

    override fun searchMangaNextPageSelector() = "ul.pagination li:last-child a"

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = when {
            document.select(searchMangaSelector()).isEmpty() -> document.select("ul li").map { element ->
                searchMangaFromElement(element)
            }
            else -> document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }
        }
        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            document.selectFirst(selector)
        } != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.selectFirst("img")?.attr("alt")?.also { title = it }
            element.selectFirst("img")?.toOriginal()?.also { thumbnail_url = it }
            element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
        }
    }

    // details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            author = document.selectFirst("div.author a")?.text()
            status = document.selectFirst("small:contains(Status) + strong")?.text().toStatus()
            genre = document.select("div.categories a").joinToString { it.text() }
            description = document.selectFirst("div.summary-block p.about")?.text()
            thumbnail_url = document.selectFirst("div.fixed-img img")?.absUrl("src")
        }
    }

    // chapters
    override fun chapterListSelector() = "ul.chapter-list li"
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
            element.selectFirst("a")?.text()?.also { name = it }
            element.selectFirst(".time")?.text().toDate().also { date_upload = it }
        }
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".chapter-content .page-chapter img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // filters
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(),
    )

    // the list can be updated via copy($$("#glo_gnb .sub-menu a").map(el => `Pair("${el.innerText.trim()}", "${el.pathname.substr(1)}"),`).join("\n"))
    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("Action", "manga-genre-action"),
            Pair("Adaptation", "manga-genre-adaptation"),
            Pair("Adult", "manga-genre-adult"),
            Pair("Adventure", "manga-genre-adventure"),
            Pair("Boy love", "manga-genre-boy-love"),
            Pair("Comedy", "manga-genre-comedy"),
            Pair("Comic", "manga-genre-comic"),
            Pair("Cooking", "manga-genre-cooking"),
            Pair("Crime", "manga-genre-crime"),
            Pair("Doujinshi", "manga-genre-doujinshi"),
            Pair("Drama", "manga-genre-drama"),
            Pair("Ecchi", "manga-genre-ecchi"),
            Pair("Fantasy", "manga-genre-fantasy"),
            Pair("Full Color", "manga-genre-full-color"),
            Pair("Game", "manga-genre-game"),
            Pair("Gender Bender", "manga-genre-gender-bender"),
            Pair("Harem", "manga-genre-harem"),
            Pair("Historical", "manga-genre-historical"),
            Pair("Horror", "manga-genre-horror"),
            Pair("Isekai", "manga-genre-isekai"),
            Pair("Josei", "manga-genre-josei"),
            Pair("Magic", "manga-genre-magic"),
            Pair("Manga", "manga-genre-manga"),
            Pair("Manhua", "manga-genre-manhua"),
            Pair("Manhwa", "manga-genre-manhwa"),
            Pair("Martial Arts", "manga-genre-martial-arts"),
            Pair("Mature", "manga-genre-mature"),
            Pair("Mecha", "manga-genre-mecha"),
            Pair("Medical", "manga-genre-medical"),
            Pair("Mystery", "manga-genre-mystery"),
            Pair("NTR", "manga-genre-ntr"),
            Pair("Oneshot", "manga-genre-oneshot"),
            Pair("Psychological", "manga-genre-psychological"),
            Pair("Reincarnation", "manga-genre-reincarnation"),
            Pair("Romance", "manga-genre-romance"),
            Pair("School life", "manga-genre-school-life"),
            Pair("Sci-fi", "manga-genre-sci-fi"),
            Pair("Seinen", "manga-genre-seinen"),
            Pair("Shoujo", "manga-genre-shoujo"),
            Pair("Shoujo ai", "manga-genre-shoujo-ai"),
            Pair("Shounen", "manga-genre-shounen"),
            Pair("Shounen ai", "manga-genre-shounen-ai"),
            Pair("Slice Of Life", "manga-genre-slice-of-life"),
            Pair("Smut", "manga-genre-smut"),
            Pair("Soft Yaoi", "manga-genre-soft-yaoi"),
            Pair("Soft Yuri", "manga-genre-soft-yuri"),
            Pair("Sports", "manga-genre-sports"),
            Pair("Super Power", "manga-genre-super-power"),
            Pair("Supernatural", "manga-genre-supernatural"),
            Pair("SURVIVAL", "manga-genre-survival"),
            Pair("Time travel", "manga-genre-time-travel"),
            Pair("Tragedy", "manga-genre-tragedy"),
            Pair("Villainess", "manga-genre-villainess"),
            Pair("Webtoon", "manga-genre-webtoon"),
            Pair("Webtoons", "manga-genre-webtoons"),
            Pair("Yaoi", "manga-genre-yaoi"),
        ),
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
