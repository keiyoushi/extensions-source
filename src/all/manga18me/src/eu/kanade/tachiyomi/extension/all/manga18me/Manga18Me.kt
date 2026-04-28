package eu.kanade.tachiyomi.extension.all.manga18me

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

open class Manga18Me(override val lang: String) : HttpSource() {
    override val name = "Manga18.me"
    override val baseUrl = "https://manga18.me"
    override val supportsLatest = true
    override val client = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/$page?orderby=trending", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select("div.page-item-detail")
        val hasNextPage = document.selectFirst(".next") != null
        if (lang == "en") {
            val searchText = document.selectFirst("div.section-heading h1")?.text().orEmpty()
            val raw = document.selectFirst("div.canonical")?.attr("href").orEmpty()
            return MangasPage(
                entries.filter { element ->
                    val href = element.selectFirst("div.item-thumb.wleft a")?.attr("href").orEmpty()
                    searchText.lowercase().contains("raw") || raw.contains("raw") || !href.contains("raw")
                }.map(::parseMangaFromElement),
                hasNextPage,
            )
        }
        return MangasPage(entries.map(::parseMangaFromElement), hasNextPage)
    }

    private fun parseMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("a")?.absUrl("href")?.let { setUrlWithoutDomain(it) }
        element.selectFirst("div.item-thumb.wleft img")?.attr("alt")?.let { title = it }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/$page?orderby=latest", headers)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isEmpty()) {
                var completed = false
                var raw = false
                var genre = ""
                filters.forEach {
                    when (it) {
                        is GenreFilter -> genre = it.getValue()
                        is CompletedFilter -> completed = it.state
                        is RawFilter -> raw = it.state
                        is SortFilter -> addQueryParameter("orderby", it.getValue())
                        else -> {}
                    }
                }
                if (raw) {
                    addPathSegment("raw")
                } else if (completed) {
                    addPathSegment("completed")
                } else {
                    if (genre != "manga") addPathSegment("genre")
                    addPathSegment(genre)
                }
                addPathSegment(page.toString())
            } else {
                addPathSegment("search")
                addQueryParameter("q", query)
                addQueryParameter("page", page.toString())
            }
        }.build()
        return GET(url, headers)
    }
    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst("div.post_content")
        return SManga.create().apply {
            title = document.select("div.post-title.wleft > h1").text()
            description = buildString {
                document.selectFirst("div.ss-manga")
                    ?.wholeText()
                    ?.takeIf { it != "N/A" }
                    ?.takeIf { it.isNotEmpty() }
                    ?.also {
                        append(it)
                        append("\n")
                    }
                info?.selectFirst("div.post-content_item.wleft:contains(Alternative) div.summary-content")
                    ?.text()
                    ?.takeIf { it != "Updating" }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        append("Alternative Names:\n")
                        it.split("/", ";").forEach { alt ->
                            append("- ", alt.trim())
                            append("\n")
                        }
                    }
            }
            val statusElement = info?.selectFirst("div.post-content_item.wleft:contains(Status) div.summary-content")
            status = when (statusElement?.text()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            author = info?.selectFirst("div.href-content.artist-content > a")?.text()?.takeIf { it != "Updating" }
            artist = info?.selectFirst("div.href-content.artist-content > a")?.text()?.takeIf { it != "Updating" }
            genre = info?.select("div.href-content.genres-content > a[href*=/manga-list/]")?.eachText()?.joinToString()
            thumbnail_url = document.selectFirst("div.summary_image > img")?.absUrl("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.row-content-chapter.wleft .a-h.wleft").map(::parseChapterFromElement)
    }

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private fun parseChapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")?.run {
            setUrlWithoutDomain(absUrl("href"))
            name = text()
        }
        date_upload = dateFormat.tryParse(element.selectFirst("span")?.text())
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val contents = document.select("div.read-content.wleft img")
        if (contents.isEmpty()) {
            throw Exception("Unable to find script with image data")
        }
        return contents.mapIndexed { idx, image ->
            Page(idx, imageUrl = image.attr("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
