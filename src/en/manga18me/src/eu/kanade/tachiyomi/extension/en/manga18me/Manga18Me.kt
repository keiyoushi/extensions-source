package eu.kanade.tachiyomi.extension.en.manga18me

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale

class Manga18Me : ParsedHttpSource() {

    override val name = "Manga18.me"

    override val lang = "en"

    override val baseUrl = "https://manga18.me"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/$page?orderby=trending", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document
            .select(popularMangaSelector())
            .filter { it ->
                val searchText = document.selectFirst("div.section-heading h1")?.text() ?: ""
                val raw = document.selectFirst("div.canonical")?.attr("href") ?: ""
                val title = it.selectFirst("div.item-thumb.wleft a")?.attr("href") ?: ""

                if (searchText.lowercase().contains("raw")) {
                    true
                } else if (raw.lowercase().contains("raw")) {
                    true
                } else if (title.contains("raw")) {
                    false
                } else {
                    true
                }
            }
            .map(::popularMangaFromElement)
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null

        return MangasPage(entries, hasNextPage)
    }

    override fun popularMangaSelector() = "div.page-item-detail"
    override fun popularMangaNextPageSelector() = ".next"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("div.item-thumb.wleft a")!!.attr("title")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/$page?orderby=latest", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isEmpty()) {
                var completed = false
                var raw = false
                var genre = ""
                filters.forEach {
                    when (it) {
                        is GenreFilter -> {
                            genre = it.getValue()
                        }

                        is CompletedFilter -> {
                            completed = it.state
                        }

                        is RawFilter -> {
                            raw = it.state
                        }

                        is SortFilter -> {
                            addQueryParameter("orderby", it.getValue())
                        }

                        else -> {}
                    }
                }
                if (raw) {
                    addPathSegment("raw")
                } else if (completed) {
                    addPathSegment("completed")
                } else {
                    addPathSegment("genre")
                    addPathSegment(genre)
                }
            } else {
                addPathSegment("search")
                addPathSegment(query)
            }
            addPathSegment(page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun getFilterList() = getFilters()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val info = document.selectFirst("div.post_content")!!

        title = document.select("div.post-title.wleft > h1").text()
        description = buildString {
            document.select("div.ss-manga > p")
                .eachText().onEach {
                    append(it.trim())
                    append("\n\n")
                }

            info.selectFirst("div.post-content_item.wleft:contains(Alternative) div.summary-content")
                ?.text()
                ?.takeIf { it != "Updating" && it.isNotEmpty() }
                ?.let {
                    append("Alternative Names:\n")
                    append(it.trim())
                }
        }
        status = when (info.select("div.post-content_item.wleft:contains(Status) div.summary-content").text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = info.selectFirst("div.href-content.artist-content > a")?.text()?.takeIf { it != "Updating" }
        artist = info.selectFirst("div.href-content.artist-content > a")?.text()?.takeIf { it != "Updating" }
        genre = info.select("div.href-content.genres-content > a[href*=/manga-list/]").eachText().joinToString()
        thumbnail_url = document.selectFirst("div.summary_image > img")?.absUrl("src")
    }

    override fun chapterListSelector() = "ul.row-content-chapter.wleft .a-h.wleft"

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(absUrl("href"))
            name = text()
        }
        date_upload = try {
            dateFormat.parse(element.selectFirst("span")!!.text())!!.time
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val contents = document.select("div.read-content.wleft img")
        if (contents.isEmpty()) {
            throw Exception("Unable to find script with image data")
        }

        return contents.mapIndexed { idx, image ->
            val imageUrl = image.attr("src")
            Page(idx, imageUrl = imageUrl)
        }
    }
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
