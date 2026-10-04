package eu.kanade.tachiyomi.extension.en.comichubfree

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ComicHubFree : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH)

    override suspend fun getPopularManga(page: Int) = parsePopular(
        client.get("$baseUrl/popular-comic?page=$page").asJsoup(),
    )

    private fun parsePopular(document: Document): MangasPage {
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

    override suspend fun getLatestUpdates(page: Int) = parsePopular(
        client.get("$baseUrl/new-comic?page=$page").asJsoup(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search-comic".toHttpUrl().newBuilder().apply {
            addQueryParameter("key", query)
            addQueryParameter("page", page.toString())
        }.build()
        return parsePopular(client.get(url).asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseDetails(doc), if (fetchChapters) parseChapters(doc) else chapters)
    }

    private fun parseDetails(document: Document): SManga {
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

    private suspend fun parseChapters(document: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        var document = document

        while (true) {
            document.select("div.episode-list > div > table > tbody > tr").mapTo(chapters) { element ->
                val urlElement = element.selectFirst("a")!!
                val dateElement = element.select("td:last-of-type")

                SChapter.create().apply {
                    setUrlWithoutDomain(urlElement.attr("abs:href"))
                    name = urlElement.text()
                    date_upload = dateFormat.tryParseDate(dateElement.text())
                }
            }

            val nextUrl = document.selectFirst("ul.pagination a[rel=next]:not(hidden)")?.absUrl("href")
            if (nextUrl.isNullOrEmpty()) {
                break
            }
            document = client.get(nextUrl).asJsoup()
        }

        return chapters
    }

    override suspend fun getPageList(chapter: SChapter) = client.get("$baseUrl${chapter.url}/all").asJsoup()
        .select("img.chapter_img").mapIndexed { index, element ->
            Page(index, imageUrl = element.imageAttr())
        }.distinctBy { it.imageUrl }

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
