package eu.kanade.tachiyomi.extension.en.rinkocomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class RinkoComics : ParsedHttpSource() {

    override val name = "Rinko Comics"
    override val baseUrl = "https://rinkocomics.com"
    override val lang = "en"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // =========================================================================
    //  Popular (Used for listing AND Search)
    // =========================================================================

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/comic/" else "$baseUrl/comic/page/$page/"
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "article.ac-card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("h2.ac-title a") ?: element.selectFirst("a.ac-thumb")!!

        setUrlWithoutDomain(anchor.absUrl("href"))
        title = anchor.text()
        thumbnail_url = element.selectFirst("a.ac-thumb img")?.attr("abs:src")
        genre = element.select("div.ac-genres a").joinToString { it.text() }
    }

    override fun popularMangaNextPageSelector() = "a.next.page-numbers, a.next"

    // =========================================================================
    //  Latest
    // =========================================================================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comic/page/$page/?order=update", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =========================================================================
    //  Search
    // =========================================================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.fromCallable {
            val response = client.newCall(popularMangaRequest(page)).execute()
            val details = popularMangaParse(response)
            val filteredMangas = details.mangas.filter { it.title.contains(query, true) }

            MangasPage(filteredMangas, details.hasNextPage)
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaSelector(): String = popularMangaSelector()

    // =========================================================================
    //  Manga Details
    // =========================================================================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("div.comic-info-upper h1")?.text()
            ?: document.selectFirst("h1.manga-title, h1.entry-title, h2.ac-title")?.text()
            ?: "Unknown"

        thumbnail_url = document.selectFirst("div.thumb img, div.ac-thumb img")?.attr("abs:src")

        genre = document.select("div.genres span.genre, div.ac-genres a").joinToString { it.text() }

        description = document.selectFirst("div.comic-synopsis")?.text()

        // [FIX] Removed .trim() per review request
        author = document.selectFirst("div.comic-graph span")?.text()
            ?.replace("Unknown Author", "")

        artist = author

        val statusText = document.selectFirst("div.comic-status span:nth-child(2)")?.text() ?: ""
        status = when {
            statusText.contains("ongoing", true) -> SManga.ONGOING
            statusText.contains("completed", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // =========================================================================
    //  Chapter List (AJAX Implementation)
    // =========================================================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            val chapters = mutableListOf<SChapter>()
            val visitedUrls = mutableSetOf<String>()

            // 1. Get initial page
            val request = chapterListRequest(manga)
            val response = client.newCall(request).execute()
            // [FIX] Using library extension asJsoup()
            val document = response.asJsoup()

            // 2. Parse visible chapters
            val visibleChapters = document.select(chapterListSelector())
                .map { chapterFromElement(it) }
                .filter { visitedUrls.add(it.url) }

            chapters.addAll(visibleChapters)

            // 3. Handle "Load More" via AJAX
            val loadMoreBtn = document.selectFirst("#loadMoreChaptersBtn")
            val comicId = loadMoreBtn?.attr("data-comic-id")

            var offset = loadMoreBtn?.attr("data-offset")?.toIntOrNull() ?: 10

            val ajaxHeaders = headers.newBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .add("Referer", request.url.toString())
                .add("Origin", baseUrl)
                .build()

            if (comicId != null) {
                while (true) {
                    val formBody = FormBody.Builder()
                        .add("action", "load_more_chapters")
                        .add("comic_id", comicId)
                        .add("offset", offset.toString())
                        .build()

                    val ajaxRequest = POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, formBody)

                    try {
                        val ajaxResponse = client.newCall(ajaxRequest).execute()
                        val ajaxHtml = ajaxResponse.body.string()

                        if (ajaxHtml.isBlank() || ajaxHtml.contains("no more chapters", true)) {
                            break
                        }

                        // [FIX] baseUrl included to resolve relative links
                        val ajaxDoc = Jsoup.parseBodyFragment(ajaxHtml, baseUrl)
                        val newChapters = ajaxDoc.select("li.chapter:not(.locked-chapter)")
                            .map { chapterFromElement(it) }
                            .filter { visitedUrls.add(it.url) }

                        if (newChapters.isEmpty()) {
                            break
                        }

                        chapters.addAll(newChapters)
                        offset += 10
                    } catch (e: Exception) {
                        break
                    }
                }
            }

            chapters
        }
    }

    override fun chapterListSelector() = "ul.chapters-list li.chapter:not(.locked-chapter)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val anchor = element.selectFirst("a")!!
        setUrlWithoutDomain(anchor.absUrl("href"))

        name = element.selectFirst("span.chapter-number")?.text()?.trim() ?: anchor.text()

        val dateText = element.selectFirst("span.chapter-date")?.text()?.trim() ?: ""
        date_upload = parseDate(dateText)
    }

    // =========================================================================
    //  Page List
    // =========================================================================

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("div.images-flow img.chapter-image")

        return images.mapIndexed { index, img ->
            val url = (img.attr("abs:data-src")).trim()
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // =========================================================================
    //  Helpers
    // =========================================================================

    private fun parseDate(dateStr: String): Long {
        return dateFormat.tryParse(dateStr) ?: 0L
    }
}