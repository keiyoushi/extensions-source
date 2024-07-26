package eu.kanade.tachiyomi.extension.en.kami18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale

class Kami18() : HttpSource() {

    override val name = "18Kami"

    override val lang = "en"

    override val baseUrl = "https://18kami.com"

    private val baseImageUrl = "$baseUrl/media/photos"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/albums?o=mv&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(".image-container")
        val hasNextPage = document.selectFirst(".prevnext") != null

        return MangasPage(entries.map(::popularMangaFromElement), hasNextPage)
    }

    private fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[href*=album]")!!.absUrl("href"))
        title = element.selectFirst("img")!!.attr("title")
        thumbnail_url = element.selectFirst("img")?.let { img ->
            img.absUrl("src").takeIf { !it.contains("blank") } ?: img.absUrl("data-original")
        }
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/albums?o=mr&page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addPathSegment("photos")
                addQueryParameter("main_tag", "5")
                addQueryParameter("search_query", query)
            }.build()
            return GET(url, headers)
        }
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            var type = ""
            var search = false
            filters.forEach {
                when (it) {
                    is TypeFilter -> {
                        type = it.getValue()
                    }

                    is SortFilter -> {
                        addQueryParameter("o", it.getValue())
                    }

                    is TimelineFilter -> {
                        addQueryParameter("t", it.getValue())
                    }

                    is TextFilter -> {
                        if (it.state.isNotBlank()) {
                            search = true
                            addQueryParameter("search_query", it.state.replace(",", " "))
                        }
                    }
                    else -> {}
                }
            }
            addPathSegment(if (search) "search/photos" else "albums")
            if (type.isNotEmpty()) addPathSegment(type)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = getFilters()

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()

        description = buildString {
            val desc = document.selectFirst("div[class*=p-t-5]:contains(description：)")?.ownText()?.substringAfter("：") ?: ""
            append(desc)
            append("\n\n", document.select("div[class\$=p-b-5]:contains(Pages)").text())
        }
        status = SManga.UNKNOWN
        author = document.select("div[class*=p-t-5]:contains(Author) > div").eachText().joinToString()
        genre = document.select("div[class*=p-t-5]:contains(Tags) > div:not(:contains(add))").eachText().joinToString()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val episode = doc.selectFirst(".episode")
        if (episode != null) {
            return episode.select("ul > a").reversed().mapIndexed(::chapterFromElement)
        }

        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(doc.selectFirst("[id=album_id]")!!.attr("value"))
                name = "Chapter 1"
                date_upload = try {
                    dateFormat.parse(doc.selectFirst("[itemprop=datePublished]")!!.text().substringAfter(": "))!!.time
                } catch (_: Exception) {
                    0L
                }
            },
        )
    }

    fun chapterFromElement(index: Int, element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("data-album"))
        name = "Chapter $index"
        date_upload = try {
            dateFormat.parse(element.selectFirst("span")!!.text())!!.time
        } catch (_: Exception) {
            0L
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                val contents = document.select("[id*=pageselect] > option")

                contents.mapIndexed { idx, image ->
                    val filename = image.attr("data-page")
                    Page(idx, imageUrl = "$baseImageUrl/${chapter.url}/$filename")
                }
            }
    }
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + "/photo/" + chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
