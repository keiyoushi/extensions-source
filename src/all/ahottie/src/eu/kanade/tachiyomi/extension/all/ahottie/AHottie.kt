package eu.kanade.tachiyomi.extension.all.ahottie

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class AHottie : HttpSource() {
    override val baseUrl = "https://ahottie.top"
    override val lang = "all"
    override val name = "AHottie"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = popularMangaParse(response.asJsoup())

    private fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select("#main > div > div").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a")!!
                val titleEl = element.selectFirst("h2") ?: throw Exception("Title not found")
                title = titleEl.text()
                thumbnail_url = element.selectFirst(".relative img")?.absUrl("src")
                genre = element.select(".flex a").joinToString(", ") { it.text() }
                setUrlWithoutDomain(link.absUrl("href"))
                initialized = true
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith("http")) {
            val url = query.toHttpUrlOrNull()
            if (url != null && url.host == baseUrl.toHttpUrl().host) {
                return GET(url, headers)
            }
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("kw", query)
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (document.selectFirst("h1") != null && document.selectFirst("div.pl-3 > a") != null) {
            val manga = mangaDetailsParse(document).apply {
                url = response.request.url.encodedPath
            }
            return MangasPage(listOf(manga), false)
        }

        return popularMangaParse(document)
    }

    // ========================= Details =========================

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val titleEl = document.selectFirst("h1") ?: throw Exception("Title not found")
        title = titleEl.text()
        genre = document.select("div.pl-3 > a").joinToString(", ") { it.text() }
        initialized = true
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ========================= Chapters =========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                val link = document.selectFirst("link[rel=canonical]") ?: throw Exception("Chapter link not found")
                setUrlWithoutDomain(link.absUrl("href"))
                chapter_number = 0F
                name = "GALLERY"
                date_upload = DATE_FORMAT.tryParse(document.selectFirst("time")?.text())
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ========================= Pages =========================

    override fun pageListParse(response: Response): List<Page> {
        val pages = mutableListOf<Page>()
        var doc = response.asJsoup()
        while (true) {
            doc.select("#main img.block").forEach {
                pages.add(Page(pages.size, imageUrl = it.absUrl("src")))
            }
            val nextPageUrl = doc.selectFirst("a[rel=next]")?.absUrl("href") ?: ""
            if (nextPageUrl.isEmpty()) break
            doc = client.newCall(GET(nextPageUrl, headers)).execute().asJsoup()
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    }
}
