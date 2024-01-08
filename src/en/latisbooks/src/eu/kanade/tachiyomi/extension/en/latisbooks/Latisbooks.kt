package eu.kanade.tachiyomi.extension.en.latisbooks

import android.net.Uri.encode
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import rx.Observable
import java.util.Calendar

class Latisbooks : HttpSource() {

    override val name = "Latis Books"

    override val baseUrl = "https://www.latisbooks.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private val textToImageURL = "https://fakeimg.pl/1500x2126/ffffff/000000/?font=museo&font_size=42"

    private fun String.image() = textToImageURL + "&text=" + encode(this)

    private fun createManga(response: Response): SManga {
        return SManga.create().apply {
            initialized = true
            title = "Bodysuit 23"
            url = "/archive/"
            thumbnail_url = "https://images.squarespace-cdn.com/content/v1/56595108e4b01110e1cf8735/1511856223610-NSB8O5OJ1F6KPQL0ZGBH/image-asset.jpeg"
        }
    }

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                MangasPage(listOf(createManga(response)), false)
            }
    }

    override fun popularMangaRequest(page: Int): Request {
        return (GET("$baseUrl/archive/", headers))
    }

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                createManga(response)
            }
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used")

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val cal: Calendar = Calendar.getInstance()

        return response.asJsoup().select("ul.archive-item-list li a").map {
            val date: List<String> = it.attr("abs:href").split("/")
            cal.set(date[4].toInt(), date[5].toInt() - 1, date[6].toInt())

            SChapter.create().apply {
                name = it.text()
                url = it.attr("abs:href")
                date_upload = cal.timeInMillis
            }
        }
    }

    // Pages

    // Adapted from the xkcd source's wordWrap function
    private fun wordWrap(text: String) = buildString {
        var charCount = 0
        text.replace("\r\n", " ").split(' ').forEach { w ->
            if (charCount > 25) {
                append("\n")
                charCount = 0
            }
            append(w).append(' ')
            charCount += w.length + 1
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val blocks = response.asJsoup().select("div.content-wrapper div.row div.col")

        // Handle multiple images per page (e.g. Page 23+24)
        val pages = blocks.select("div.image-block-wrapper img")
            .mapIndexed { i, it -> Page(i, "", it.attr("abs:data-src")) }
            .toMutableList()

        val numImages = pages.size

        // Add text above/below the image as xkcd-esque text pages after the image itself
        pages.addAll(
            blocks.select("div.html-block")
                .map { it.select("div.sqs-block-content").first()!! }
                // Some pages have empty html blocks (e.g. Page 1), so ignore them
                .filter { it.childrenSize() > 0 }
                .mapIndexed { i, it -> Page(i + numImages, "", wordWrap(it.text()).image()) }
                .toList(),
        )

        return pages.toList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
