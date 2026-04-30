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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiNexus : HttpSource() {

    override val name = "HentaiNexus"

    override val lang = "en"

    override val baseUrl = "https://hentainexus.com"

    override val supportsLatest = false

    // Images on this site go through the free Jetpack Photon CDN.
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

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container .column").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst(".card-header-title")!!.text()
                thumbnail_url = element.selectFirst(".card-image img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a.pagination-next[href]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_ID_SEARCH)) {
        val id = query.removePrefix(PREFIX_ID_SEARCH)
        client.newCall(GET("$baseUrl/view/$id", headers)).asObservableSuccess()
            .map { MangasPage(listOf(mangaDetailsParse(it).apply { url = "/view/$id" }), false) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val actualPage = page + (filters.firstInstanceOrNull<OffsetPageFilter>()?.state?.toIntOrNull() ?: 0)
            if (actualPage > 1) {
                addPathSegments("page/$actualPage")
            }
            addQueryParameter("q", (combineQuery(filters) + query).trim())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    private val tagCountRegex = Regex("""\s*\([\d,]+\)$""")

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        val table = document.selectFirst(".view-page-details")!!

        title = document.selectFirst("h1.title")!!.text()

        val artists = table.select("td.viewcolumn:contains(Artist) + td a").map { it.ownText() }
        val authors = table.select("td.viewcolumn:contains(Author) + td a").map { it.ownText() }
        author = (authors + artists).distinct().joinToString().takeIf { it.isNotEmpty() }
        artist = null

        description = buildString {
            listOf("Circle", "Event", "Magazine", "Parody", "Publisher", "Pages", "Favorites").forEach { key ->
                val cell = table.selectFirst("td.viewcolumn:contains($key) + td")
                cell
                    ?.ownText()
                    ?.ifEmpty { cell.selectFirst("a")?.ownText() }
                    ?.let { appendLine("$key: $it") }
            }

            table.selectFirst("td.viewcolumn:contains(Description) + td")?.text()?.let {
                appendLine()
                append(it)
            }
        }.trim()

        genre = table.select("span.tag a").joinToString {
            it.text().replace(tagCountRegex, "")
        }.takeIf { it.isNotEmpty() }

        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        status = SManga.COMPLETED

        thumbnail_url = document.selectFirst("figure.image img")?.attr("src")
    }

    private val dateFormat by lazy {
        SimpleDateFormat("dd MMMM yyyy", Locale.US)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val table = document.selectFirst(".view-page-details")!!
        val dateUploadStr = table.selectFirst("td.viewcolumn:contains(Published) + td")?.text()

        val id = response.request.url.pathSegments.last()
        return listOf(
            SChapter.create().apply {
                url = "/read/$id"
                name = "Chapter"
                date_upload = dateFormat.tryParse(dateUploadStr)
            },
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(initReader)")?.data()
            ?: throw Exception("Could not find initReader script; the page structure may have changed")
        val encoded = script.substringAfter("initReader(\"").substringBefore("\",")
        val data = Utils.decryptData(encoded)

        return json.parseToJsonElement(data).jsonArray
            .filter { it.jsonObject["type"]!!.jsonPrimitive.content == "image" }
            .mapIndexed { i, it -> Page(i, imageUrl = it.jsonObject["image"]!!.jsonPrimitive.content) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

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
