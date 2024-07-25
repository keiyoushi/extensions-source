package eu.kanade.tachiyomi.extension.en.readcomicsbook

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReadComicsBook : HttpSource() {
    override val name = "Read Comics Book"
    override val lang = "en"
    override val baseUrl = "https://readcomicsplus.net"
    override val supportsLatest = true
    override val client = network.cloudflareClient.newBuilder()
        .readTimeout(1L, TimeUnit.MINUTES)
        .build()
    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        val url = buildString {
            append(baseUrl)
            append("/popular-comics")
            if (page > 1) {
                append("?page=")
                append(page.toString())
            }
        }

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()

        return MangasPage(
            mangas = doc.select(".manga-list .manga-thumb a").map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = it.attr("title")
                    thumbnail_url = it.selectFirst("img")?.attr("data-original")
                        ?.replace("http://", "https://")
                }
            },
            hasNextPage = doc.selectFirst(".page-pagination .next-page") != null,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = buildString {
            append(baseUrl)
            append("/comic-updates")
            if (page > 1) {
                append("?page=")
                append(page.toString())
            }
        }

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            val url = "$baseUrl/ajax/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .build()

            GET(url, headers)
        } else {
            val url = buildString {
                append(baseUrl)
                append("/genre/")
                append(
                    filters.filterIsInstance<GenreFilter>().first().selected,
                )
                if (page > 1) {
                    append("?page=")
                    append(page.toString())
                }
            }

            GET(url, headers)
        }
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            GenreFilter(),
            Filter.Header("Filters don't work with text search"),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments[0] == "genre") {
            return popularMangaParse(response)
        }

        val res = json.decodeFromStream<Data<List<Comic>>>(response.body.byteStream())

        return MangasPage(
            mangas = res.data.map {
                SManga.create().apply {
                    url = "/comic/${it.slug}"
                    title = it.title
                    thumbnail_url = it.cover ?: "$baseUrl/images/sites/default.jpg"
                }
            },
            hasNextPage = false,
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()

        return SManga.create().apply {
            title = doc.selectFirst(".headline h1")!!.text()
            author = doc.selectFirst("div.meta-data.mt-author")?.ownText()
            status = with(doc.selectFirst("div.meta-data:has(> label:contains(status))")?.ownText()) {
                when {
                    equals("ongoing", true) -> SManga.ONGOING
                    equals("completed", true) -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            thumbnail_url = doc.selectFirst("div.manga-thumb img")?.absUrl("data-original")
                ?.replace("http://", "https://")
            genre = doc.select("div.meta-data a[href*=/genre/]").eachText().joinToString()
            description = buildString {
                doc.selectFirst(".summary-content")?.text()?.let(::append)
                if (isNotBlank()) {
                    append("\n\n")
                }
                doc.selectFirst("div.meta-data:has(> label:contains(Other Names))")
                    ?.text()?.let(::appendLine)
                doc.selectFirst("div.meta-data.view")
                    ?.text()?.let(::appendLine)
                doc.selectFirst("div.rating")?.text()?.let {
                    append("Rating: ")
                    append(it, "\n")
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()

        return doc.select("ul.chapter-list li").drop(1).map {
            SChapter.create().apply {
                with(it.selectFirst("a")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    name = text()
                }
                date_upload = it.selectFirst("span.time")?.text().parseDate()
            }
        }
    }

    private fun String?.parseDate(): Long {
        if (isNullOrBlank()) return 0L

        return try {
            dateFormat.parse(this)?.time ?: 0L
        } catch (_: ParseException) {
            0L
        }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyy", Locale.ENGLISH)

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()

        return doc.select(".page-chapter img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.attr("data-original"))
        }
    }

    override fun imageRequest(page: Page): Request {
        var url = page.imageUrl!!

        if (url.toHttpUrl().host.contains("blogspot") && url.contains("s1600")) {
            url = url.replace("s1600", "s0")
        }

        return GET(url, headers)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}
