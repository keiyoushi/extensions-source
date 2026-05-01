package eu.kanade.tachiyomi.extension.pt.bakai

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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Bakai : HttpSource() {

    override val name = "Bakai"

    override val baseUrl = "https://bakai.org"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // The popular section is a carousel widget on the homepage
        val mangas = document.select("li.mostViewedArticlesItem").map { element ->
            val a = element.selectFirst("h3.ipsTruncate a") ?: throw Exception("Title URL not found")
            SManga.create().apply {
                title = a.text()
                setUrlWithoutDomain(a.attr("href"))
                thumbnail_url = element.selectFirst("span.ipsThumb img")?.attr("abs:src")
            }
        }

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/home/"
        } else {
            "$baseUrl/home/page/$page/"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("ul.ipsGrid > li.ipsGrid_span4").map { element ->
            val a = element.selectFirst("h2.ipsType_pageTitle a") ?: throw Exception("Title URL not found")
            SManga.create().apply {
                title = a.text()
                setUrlWithoutDomain(a.attr("href"))
                thumbnail_url = element.selectFirst("div.cCmsRecord_image img")?.attr("abs:src")
            }
        }

        val hasNextPage = document.selectFirst("li.ipsPagination_next:not(.ipsPagination_inactive) > a") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("quick", "1")
            .addQueryParameter("search_and_or", "and")
            .addQueryParameter("sortby", "relevancy")
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // Filter out everything that is not specifically a "Hentai" post (i.e. exclude reviews/videos)
        val mangas = document.select("ol[data-role=resultsContents] > li.ipsStreamItem")
            .filter { it.selectFirst("span.ipsStreamItem_contentType i.fa-file-text") != null }
            .map { element ->
                val a = element.selectFirst("h2.ipsStreamItem_title a") ?: throw Exception("Title URL not found")

                SManga.create().apply {
                    title = a.text()
                    setUrlWithoutDomain(a.attr("href"))

                    val imgNode = element.selectFirst("span.ipsThumb img, img.ipsStream_image")
                    thumbnail_url = imgNode?.let {
                        val dataSrc = it.attr("abs:data-src")
                        dataSrc.ifEmpty { it.attr("abs:src") }
                    } ?: element.selectFirst("span.ipsThumb")?.attr("abs:data-background-src")
                }
            }

        val hasNextPage = document.selectFirst("li.ipsPagination_next:not(.ipsPagination_inactive) > a") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.ipsType_pageTitle span.ipsContained")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.cCmsRecord_image img")?.attr("abs:src")

            author = document.selectFirst("p:has(strong:contains(Artist:))")?.text()?.substringAfter("Artist:")?.trim()

            val type = document.selectFirst("p:has(strong:contains(Type:))")?.text()?.substringAfter("Type:")?.trim()
            val color = document.selectFirst("p:has(strong:contains(Color:))")?.text()?.substringAfter("Color:")?.trim()
            val tagsStr = document.selectFirst("p:has(strong:contains(Tags:))")?.text()?.substringAfter("Tags:")?.trim()
            val parody = document.selectFirst("p:has(strong:contains(Parody:))")?.text()?.substringAfter("Parody:")?.trim()

            genre = listOfNotNull(type, color, parody, tagsStr)
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")

            description = document.selectFirst("section.ipsType_richText")?.text()?.takeIf { it != "-" }

            // Site behaves mainly as a gallery
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        // Single post gallery structure
        val chapter = SChapter.create().apply {
            name = document.selectFirst("h1.ipsType_pageTitle span.ipsContained")?.text() ?: "Chapter"
            setUrlWithoutDomain(response.request.url.toString())

            val dateStr = document.selectFirst("time")?.attr("datetime")
            if (dateStr != null) {
                date_upload = dateFormat.tryParse(dateStr)
            }
        }

        return listOf(chapter)
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("div.pular img.ipsImage").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
