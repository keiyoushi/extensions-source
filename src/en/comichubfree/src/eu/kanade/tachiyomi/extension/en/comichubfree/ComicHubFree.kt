package eu.kanade.tachiyomi.extension.en.comichubfree

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ComicHubFree : HttpSource() {
    private val dateFormat = SimpleDateFormat("d-MMM-yyyy", Locale.getDefault())

    override val baseUrl = "https://comichubfree.com"

    override val lang = "en"

    override val name = "ComicHubFree"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/popular-comic".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".movie-list-index > .cartoon-box:has(.detail)").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.imageAttr()
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]:not(hidden)") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/new-comic".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search-comic".toHttpUrl().newBuilder().apply {
            addQueryParameter("key", query)
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()

        while (true) {
            document.select("div.episode-list > div > table > tbody > tr").mapTo(chapters) { element ->
                val urlElement = element.selectFirst("a")!!
                val dateElement = element.select("td:last-of-type")

                SChapter.create().apply {
                    setUrlWithoutDomain(urlElement.attr("abs:href"))
                    name = urlElement.text()
                    date_upload = dateFormat.tryParse(dateElement.text())
                }
            }

            val nextUrl = document.selectFirst("ul.pagination a[rel=next]:not(hidden)")?.absUrl("href")
            if (nextUrl.isNullOrEmpty()) {
                break
            }
            document = client.newCall(GET(nextUrl, headers)).execute().asJsoup()
        }

        return chapters
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.movie-info") ?: return SManga.create()
        val seriesInfoElement = infoElement.selectFirst("div.series-info")
        val seriesDescriptionElement = infoElement.selectFirst("div#film-content")

        val authorElement = seriesInfoElement?.select("dt:contains(Authors:) + dd")
        val statusElement = seriesInfoElement?.select("dt:contains(Status:) + dd")

        val image = seriesInfoElement?.selectFirst("img")

        return SManga.create().apply {
            description = seriesDescriptionElement?.text()
            thumbnail_url = image?.imageAttr()
            author = authorElement?.text()
            status = parseStatus(statusElement?.text().orEmpty())
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = (baseUrl + chapter.url + "/all").toHttpUrl()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.chapter_img").mapIndexed { index, element ->
            Page(index, imageUrl = element.imageAttr())
        }.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    private fun parseStatus(status: String): Int = when (status) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun Element.imageAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }
}
