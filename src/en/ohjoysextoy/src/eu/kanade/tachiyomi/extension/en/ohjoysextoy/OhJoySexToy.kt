package eu.kanade.tachiyomi.extension.en.ohjoysextoy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

private val MULTI_SPACE_REGEX = "\\s{6,}".toRegex()

class OhJoySexToy : HttpSource() {

    override val name = "Oh Joy Sex Toy"
    override val baseUrl = "https://www.ohjoysextoy.com"
    override val lang = "en"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH)

    // Browse

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category/comic/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".comicthumbwrap").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".comicarchiveframe > a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst(".comicthumbdate")!!.text().substringBefore(" by")
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".pagenav-left a") != null

        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#MattsRecentComicsBar > ul > div").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".comicarchiveframe > a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst(".comicthumbdate")!!.text().substringBefore(" by")
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, false)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("h2.post-title").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text().substringBefore(" by")
            }
        }

        return MangasPage(mangas, false)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val ogTitle = document.selectFirst("meta[property=\"og:title\"]")!!.attr("content")

            title = ogTitle.substringBefore(" by")
            author = ogTitle.substringAfter("by ", "").takeIf { it.isNotEmpty() }
            description = parseDescription(document)
            genre = document.select("meta[property=\"article:section\"]:not(:first-of-type)")
                .eachAttr("content")
                .joinToString()
            status = SManga.COMPLETED
            thumbnail_url = document.selectFirst("meta[property=\"og:image\"]")?.absUrl("content")
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            setUrlWithoutDomain(document.selectFirst("meta[property=\"og:url\"]")!!.absUrl("content"))
        }
    }

    private fun parseDescription(document: Document): String = buildString {
        val desc = document.selectFirst("meta[property=\"og:description\"]")
            ?.attr("content")
            ?.split(MULTI_SPACE_REGEX)
            ?.firstOrNull()

        if (!desc.isNullOrEmpty()) {
            append(desc)
            append("...\n\n")
        }

        val authorLinks = document.select(".entry div.ui-tabs div a")
        if (authorLinks.isNotEmpty()) {
            val authorCredits = authorLinks.joinToString("\n") { link ->
                "${link.text()}: ${link.absUrl("href")}"
            }
            append(authorCredits)
            append("\n\n")
        }

        append("(Full description and credits in WebView)")
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val dateString = document.selectFirst(".post-date")?.text()

        return listOf(
            SChapter.create().apply {
                name = document.title()
                scanlator = document.selectFirst(".post-author a")?.text()
                date_upload = dateFormat.tryParse(dateString)
                setUrlWithoutDomain(response.request.url.toString())
            },
        )
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.comicpane img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
