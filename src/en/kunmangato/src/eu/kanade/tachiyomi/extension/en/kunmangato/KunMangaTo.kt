package eu.kanade.tachiyomi.extension.en.kunmangato

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

class KunMangaTo : ParsedHttpSource() {
    override val name = "KunMangaTo"
    override val baseUrl = "https://kunmanga.to"
    override val lang = "en"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val dateFormat by lazy {
        SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
    }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = super.popularMangaParse(response).mangas.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun popularMangaSelector(): String = ".sidebar-box-popular article"

    override fun popularMangaFromElement(element: Element): SManga {
        val a = element.selectFirst("a.manga")!!
        return SManga.create().apply {
            title = a.text()
            setUrlWithoutDomain(a.absUrl("href"))
            thumbnail_url = element.selectFirst("img")?.absUrl("src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", query.trim())
            .addQueryParameter("page", page.toString())

        filters.filterIsInstance<UriPartFilter>().forEach { filter ->
            urlBuilder.addQueryParameter(
                filter.toQueryParam(),
                filter.toUriPart(),
            )
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaSelector(): String = "article .card-manga"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = "ul.pagination-primary a[rel=next]"

    override fun latestUpdatesRequest(page: Int): Request {
        val urlBuilder = "$baseUrl/latest".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        return GET(urlBuilder.build(), headers)
    }

    override fun latestUpdatesSelector(): String = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = searchMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst(".page-heading")!!.text()
            author = document.selectFirst("p.mb-1:nth-child(1)")!!.text().drop(9)
            description = document.getElementById("manga-description")!!.text()
            genre =
                document.select("a.manga-genre").joinToString { element -> element.text() }
            status =
                if (document.select("*:has(~ #manga-description) p span:contains(Status:) ~ span").size > 0) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("img.text-end")!!.attr("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chaptersDocument =
            Jsoup.parseBodyFragment(document.selectFirst(chapterListSelector())!!.attr("value"))
        val chapterItems = chaptersDocument.select(".chapter-item")
        return chapterItems.map { element ->
            chapterFromElement(element)
        }
    }

    override fun chapterListSelector(): String = "#chapterList"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            name = element.getElementsByTag("h3").first()!!.text()
            element.selectFirst(".text-muted")?.also {
                date_upload = it.text().parseChapterDate()
            }
        }
    }

    private fun String?.parseChapterDate(): Long {
        if (this == null) return 0L
        return try {
            dateFormat.parse(this)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterIdx = response.request.url.toString().substringBefore(".html").substringAfterLast("-")
        val form = FormBody.Builder()
            .add("chapterIdx", chapterIdx)
            .build()

        val chaptersRequest = POST(
            "$baseUrl/chapter-resources",
            headers.newBuilder().add(
                "X-CSRF-TOKEN",
                document.selectFirst("head > meta[name=\"csrf-token\"]")!!.attr("content"),
            ).add(
                "Cookie",
                response.headers.values("set-cookie")
                    .find { value -> value.startsWith("kunmanga_session") }!!,
            ).build(),
            form,
        )
        val chaptersResponse = client.newCall(chaptersRequest).execute()
        val chapterResources =
            json.decodeFromString<KunMangaToChapterResourcesDto>(chaptersResponse.body.string())
        return chapterResources.data.resources.map { resource ->
            Page(resource.id, imageUrl = resource.thumb)
        }
    }

    private var filterValues: Map<KunMangaToFilter, List<OptionValueOptionNamePair>>? = null

    private var fetchFilterValuesAttempts: Int = 0

    private fun parseFilters(document: Document): Map<KunMangaToFilter, List<OptionValueOptionNamePair>> {
        return KunMangaToFilter.values().associateWith {
            document.select("[name=\"${it.queryParam}\"] option")
                .map { option -> option.attr("value") to option.text() }
        }
    }

    private fun fetchFiltersValues() {
        if (fetchFilterValuesAttempts < 3 && filterValues == null) {
            thread {
                try {
                    filterValues = parseFilters(
                        client.newCall(searchMangaRequest(1, "", FilterList())).execute().asJsoup(),
                    )
                } finally {
                    fetchFilterValuesAttempts++
                }
            }
        }
    }

    override fun getFilterList(): FilterList {
        fetchFiltersValues()
        return if (filterValues != null) {
            FilterList(
                filterValues!!.map { filterValue ->
                    UriPartFilter(
                        filterValue.key,
                        filterValue.value,
                    )
                },
            )
        } else {
            FilterList(Filter.Header("Press 'Reset' to attempt to fetch the filters"))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    override fun pageListParse(document: Document): List<Page> {
        throw UnsupportedOperationException()
    }
}
