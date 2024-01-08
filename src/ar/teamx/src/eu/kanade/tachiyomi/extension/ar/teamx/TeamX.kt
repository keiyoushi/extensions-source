package eu.kanade.tachiyomi.extension.ar.teamx

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TeamX : ParsedHttpSource() {

    override val name = "Team X"

    override val baseUrl = "https://team1x12.com"

    override val lang = "ar"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(10, 1, TimeUnit.SECONDS)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series/" + if (page > 1) "?page=$page" else "", headers)
    }

    override fun popularMangaSelector() = "div.listupd div.bsx"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("a").attr("title")
            setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
            thumbnail_url = element.select("img").let {
                if (it.hasAttr("data-src")) {
                    it.attr("abs:data-src")
                } else {
                    it.attr("abs:src")
                }
            }
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    // Latest

    private val titlesAdded = mutableSetOf<String>()

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) titlesAdded.clear()

        return GET(baseUrl + if (page > 1) "?page=$page" else "", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val unfilteredManga = document.select(latestUpdatesSelector())

        val mangaList = unfilteredManga.map { element ->
            latestUpdatesFromElement(element)
        }.distinctBy {
            it.title
        }.filter {
            !titlesAdded.contains(it.title)
        }

        titlesAdded.addAll(mangaList.map { it.title })

        return MangasPage(mangaList, document.select(latestUpdatesNextPageSelector()).isNotEmpty())
    }

    override fun latestUpdatesSelector() = "div.last-chapter div.box"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            val linkElement = element.select("div.info a")
            title = linkElement.select("h3").text()
            setUrlWithoutDomain(linkElement.first()!!.attr("href"))
            thumbnail_url = element.select("div.imgu img").first()!!.absUrl("src")
        }
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/ajax/search?keyword=$query", headers)
    }

    override fun searchMangaSelector() = "li.list-group-item"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            val urlAndText = element.select("div.ms-2 a")
            title = urlAndText.text()
            setUrlWithoutDomain(urlAndText.first()!!.absUrl("href"))
            thumbnail_url = element.select("a img").first()!!.absUrl("src")
        }
    }

    // doesnt matter as there is no next page
    override fun searchMangaNextPageSelector(): String? = null

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("div.author-info-title h1").text()
            description = document.select("div.review-content").text()
            if (description.isNullOrBlank()) {
                description = document.select("div.review-content p").text()
            }
            genre = document.select("div.review-author-info a").joinToString { it.text() }
            thumbnail_url = document.select("div.text-right img").first()!!.absUrl("src")
        }
    }

    // Chapters
    private fun chapterNextPageSelector() = popularMangaNextPageSelector()

    override fun chapterListParse(response: Response): List<SChapter> {
        val allElements = mutableListOf<Element>()
        var document = response.asJsoup()

        while (true) {
            val pageChapters = document.select(chapterListSelector())
            if (pageChapters.isEmpty()) {
                break
            }

            allElements += pageChapters

            val hasNextPage = document.select(chapterNextPageSelector()).isNotEmpty()
            if (!hasNextPage) {
                break
            }

            val nextUrl = document.select(chapterNextPageSelector()).attr("href")

            document = client.newCall(GET(nextUrl, headers)).execute().asJsoup()
        }

        return allElements.map { chapterFromElement(it) }
    }

    private val chapterFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault())

    override fun chapterListSelector() = "div.eplister ul a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            val chpNum = element.select("div.epl-num").text()
            val chpTitle = element.select("div.epl-title").text()

            name = when (chpNum.isNullOrBlank()) {
                true -> chpTitle
                false -> "$chpNum - $chpTitle"
            }

            date_upload = parseChapterDate(element.select("div.epl-date").text())

            setUrlWithoutDomain(element.attr("href"))
        }
    }

    private fun parseChapterDate(date: String): Long {
        return runCatching {
            chapterFormat.parse(date)?.time
        }.getOrNull() ?: 0
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.image_list img[src]").mapIndexed { i, img ->
            Page(i, "", img.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
