package eu.kanade.tachiyomi.extension.th.mikudoujin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MikuDoujin : HttpSource() {

    override val baseUrl: String = "https://miku-doujin.com"

    override val lang: String = "th"
    override val name: String = "MikuDoujin"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.col-6.inz-col").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                title = a.selectFirst("div.inz-title")?.text() ?: ""
                thumbnail_url = a.selectFirst("img")?.attr("abs:src")
                initialized = false
            }
        }
        val hasNextPage = document.selectFirst("button.btn-secondary") != null

        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    private fun genre(name: String): String = if (name != "สาวใหญ่/แม่บ้าน") {
        URLEncoder.encode(name, "UTF-8")
    } else {
        name
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith("http")) {
            return GET(query, headers)
        }
        return GET("$baseUrl/genre/${genre(query)}/?page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // Handle URL intent searching where it redirects or resolves directly to the details page
        if (document.selectFirst("div.sr-card-body div.col-md-4 img") != null && document.selectFirst("div.col-6.inz-col") == null) {
            val manga = SManga.create().apply {
                url = response.request.url.encodedPath
                title = document.title()
                thumbnail_url = document.selectFirst("div.sr-card-body div.col-md-4 img")?.attr("abs:src")
                initialized = false
            }
            return MangasPage(listOf(manga), false)
        }

        return popularMangaParse(response)
    }

    // Manga summary page

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.sr-card-body") ?: return SManga.create()

        return SManga.create().apply {
            title = document.title()
            author = infoElement.select("div.col-md-8 p a.badge-secondary").getOrNull(2)?.ownText()
            artist = author

            genre = infoElement.select("div.col-md-8 div.tags a").joinToString { it.text() }
            description = infoElement.selectFirst("div.col-md-8")?.ownText()
            thumbnail_url = infoElement.selectFirst("div.col-md-4 img")?.attr("abs:src")

            val tableEpisodes = document.select("table.table-episode tr td a")
            status = if (tableEpisodes.isEmpty()) {
                SManga.COMPLETED
            } else {
                val hasEnd = tableEpisodes.any { it.text().split(" ").last() == "จบ" }
                if (hasEnd) SManga.COMPLETED else SManga.UNKNOWN
            }

            initialized = true
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select("table.table-episode tr")

        if (elements.isEmpty()) {
            return listOf(
                SChapter.create().apply {
                    url = response.request.url.encodedPath
                    name = "Chapter 1"
                    chapter_number = 1.0f
                },
            )
        }

        return elements.mapIndexedNotNull { idx, element ->
            val a = element.selectFirst("td a") ?: return@mapIndexedNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                name = a.text()
                if (name.isEmpty()) {
                    chapter_number = 0.0f
                } else {
                    val lastWord = name.split(" ").last()
                    chapter_number = lastWord.toFloatOrNull() ?: (idx + 1).toFloat()
                }
            }
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div#v-pills-tabContent img.lazy").mapIndexed { i, img ->
            val url = if (img.hasAttr("data-src")) {
                img.attr("abs:data-src")
            } else {
                img.attr("abs:src")
            }
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
