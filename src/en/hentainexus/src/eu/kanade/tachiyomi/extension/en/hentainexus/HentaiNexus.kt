package eu.kanade.tachiyomi.extension.en.hentainexus

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy

class HentaiNexus : ParsedHttpSource() {

    override val name = "HentaiNexus"

    override val lang = "en"

    override val baseUrl = "https://hentainexus.com"

    override val supportsLatest = false

    // Images on this site goes through the free Jetpack Photon CDN.
    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = GET(
        baseUrl + (if (page > 1) "/page/$page" else ""),
        headers,
    )

    override fun popularMangaSelector() = ".container .column"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst(".card-header-title")!!.text()
        thumbnail_url = element.selectFirst(".card-image img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = "a.pagination-next[href]"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(GET("$baseUrl/view/$id", headers)).asObservableSuccess()
                .map { MangasPage(listOf(mangaDetailsParse(it).apply { url = "/view/$id" }), false) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val actualPage = page + (filters.filterIsInstance<OffsetPageFilter>().firstOrNull()?.state?.toIntOrNull() ?: 0)
            if (actualPage > 1) {
                addPathSegments("page/$actualPage")
            }

            addQueryParameter("q", (combineQuery(filters) + query).trim())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private val tagCountRegex = Regex("""\s*\([\d,]+\)$""")

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val table = document.selectFirst(".view-page-details")!!

        title = document.selectFirst("h1.title")!!.text()
        artist = table.select("td.viewcolumn:contains(Artist) + td a").joinToString { it.ownText() }
        author = table.select("td.viewcolumn:contains(Author) + td a").joinToString { it.ownText() }
        description = buildString {
            listOf("Circle", "Event", "Magazine", "Parody", "Publisher", "Pages", "Favorites").forEach { key ->
                val cell = table.selectFirst("td.viewcolumn:contains($key) + td")

                cell
                    ?.ownText()
                    ?.ifEmpty { cell.selectFirst("a")!!.ownText() }
                    ?.let { appendLine("$key: $it") }
            }
            appendLine()

            table.selectFirst("td.viewcolumn:contains(Description) + td")?.text()?.let {
                appendLine(it)
            }
        }
        genre = table.select("span.tag a").joinToString {
            it.text().replace(tagCountRegex, "")
        }
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        status = SManga.COMPLETED

        thumbnail_url = document.selectFirst("figure.image img")?.attr("src")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.split("/").last()

        return Observable.just(
            listOf(
                SChapter.create().apply {
                    url = "/read/$id"
                    name = "Chapter"
                },
            ),
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(initReader)")?.data()
            ?: throw Exception("Could not find chapter data")
        val encoded = script.substringAfter("initReader(\"").substringBefore("\",")
        val data = HentaiNexusUtils.decryptData(encoded)

        return json.parseToJsonElement(data).jsonArray
            .filter { it.jsonObject["type"]!!.jsonPrimitive.content == "image" }
            .mapIndexed { i, it -> Page(i, imageUrl = it.jsonObject["image"]!!.jsonPrimitive.content) }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header(
            """
            Separate items with commas (,)
            Prepend with dash (-) to exclude
            For items with multiple words, surround them with double quotes (")
            """.trimIndent(),
        ),
        TagFilter(),
        ArtistFilter(),
        AuthorFilter(),
        CircleFilter(),
        EventFilter(),
        ParodyFilter(),
        MagazineFilter(),
        PublisherFilter(),

        Filter.Separator(),
        OffsetPageFilter(),
    )

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
