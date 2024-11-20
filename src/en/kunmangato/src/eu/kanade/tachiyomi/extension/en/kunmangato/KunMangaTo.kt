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
import okhttp3.Headers
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

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }.associateBy({ it.url }, { it }).values.toList()
        return MangasPage(mangas, false)
    }

    override fun popularMangaSelector(): String {
        return ".sidebar-box-popular article"
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val a = element.select("a.manga").first()!!
        return SManga.create().apply {
            title = a.text()
            url = a.attr("href").removePrefix(baseUrl)
            thumbnail_url = element.select("img").first()!!.attr("src")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", query.trim())
            .addQueryParameter("page", page.toString())

        filters.filterIsInstance<UriPartFilter>().forEach { filter ->
            urlBuilder.addQueryParameter(
                filter.internalName,
                filter.toUriPart(),
            )
        }

        return GET(urlBuilder.build())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }
        val nextPageElement = document.select(".pagination .page-item").last()
        return MangasPage(
            mangas,
            nextPageElement?.hasClass("disabled")?.not() ?: false,
        )
    }

    override fun searchMangaSelector(): String {
        return "article .card-manga"
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request {
        val urlBuilder = "$baseUrl/latest".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        return GET(urlBuilder.build())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        val nextPageElement = document.select(".pagination .page-item").last()
        return MangasPage(
            mangas,
            nextPageElement?.hasClass("disabled")?.not() ?: false,
        )
    }

    override fun latestUpdatesSelector(): String = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select(".page-heading").first()!!.text()
            author = document.select("p.mb-1:nth-child(1)").first()!!.text().drop(9)
            description = document.getElementById("manga-description")!!.text()
            genre =
                document.select("a.manga-genre").joinToString(", ") { element -> element.text() }
            status =
                if (document.select(".text-success").size > 0) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.select("img.text-end").first()!!.attr("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chaptersDocument =
            Jsoup.parse("<div>" + document.getElementById("chapterList")!!.attr("value") + "</div>")
        val chapterItems = chaptersDocument.select(".chapter-item")
        return chapterItems.map { element ->
            chapterFromElement(element)
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapterName = element.getElementsByTag("h3").first()!!.text()

        return SChapter.create().apply {
            url = element.getElementsByTag("a").first()!!.attr("href").removePrefix(baseUrl)
            name = chapterName
            date_upload = element.select(".text-muted").first()!!.text().parseChapterDate()
            chapter_number = chapterName.removePrefix("Chapter ").toFloat()
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
        val form = FormBody.Builder()
            .add(
                "chapterIdx",
                response.request.url.toString().substringBefore(".html").substringAfterLast("-"),
            )
            .build()
        val chaptersRequest = POST(
            "$baseUrl/chapter-resources",
            Headers.headersOf(
                "X-CSRF-TOKEN",
                document.select("head > meta[name=\"csrf-token\"]").first()!!.attr("content"),
                "Cookie",
                response.headers.values("set-cookie")
                    .find { value -> value.startsWith("kunmanga_session") }!!,
            ),
            form,
        )
        val chaptersResponse = client.newCall(chaptersRequest).execute()
        val chapterResources =
            json.decodeFromString<KunMangaToChapterResourcesDto>(chaptersResponse.body.string())
        return chapterResources.data.resources.map { resource ->
            Page(
                resource.id,
                "",
                resource.thumb,
            )
        }
    }

    private val filterNames =
        arrayOf("manga_genre_id" to "Genre", "manga_type_id" to "Type", "status" to "Status")

    private var filterValues: Map<Pair<String, String>, List<Pair<String, String>>>? = null

    private var fetchFilterValuesAttempts: Int = 0

    private fun parseFilters(document: Document): Map<Pair<String, String>, List<Pair<String, String>>> {
        return filterNames.associateBy(
            { it },
            {
                document.select("[name=\"${it.first}\"] option")
                    .map { option -> Pair(option.attr("value"), option.text()) }
            },
        )
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
                        filterValue.key.second,
                        filterValue.key.first,
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

    override fun chapterListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun pageListParse(document: Document): List<Page> {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    override fun popularMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    override fun searchMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }
}
