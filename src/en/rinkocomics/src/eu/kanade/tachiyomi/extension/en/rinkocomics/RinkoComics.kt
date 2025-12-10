package eu.kanade.tachiyomi.extension.en.rinkocomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
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

    private val dateFormatFull = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val dateFormatShort = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // =========================================================================
    //  Popular
    // =========================================================================

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/comic/" else "$baseUrl/comic/page/$page/"
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "article.ac-card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("h2.ac-title a") ?: element.selectFirst("a.ac-thumb")!!

        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text().trim()
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
            ?: document.selectFirst("h1.manga-title, h1.entry-title, h2.ac-title")!!.text()

        thumbnail_url = document.selectFirst("div.thumb img, div.ac-thumb img")?.attr("abs:src")

        genre = document.select("div.genres span.genre, div.ac-genres a").joinToString { it.text() }

        description = document.selectFirst("div.comic-synopsis")?.text()?.trim()

        author = document.selectFirst("div.comic-graph span")?.text()
            ?.replace("Unknown Author", "")
            ?.trim()

        artist = author

        val statusText = document.selectFirst("div.comic-status span:nth-child(2)")?.text() ?: ""
        status = when {
            statusText.contains("ongoing", true) -> SManga.ONGOING
            statusText.contains("completed", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // =========================================================================
    //  Chapter List
    // =========================================================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            val chapters = mutableListOf<SChapter>()

            val request = chapterListRequest(manga)
            val response = client.newCall(request).execute()
            val document = response.asJsoup()

            // Find max chapter number from visible list (using selector that excludes locked)
            val latestChapterElement = document.selectFirst(chapterListSelector())
            val latestChapterName = latestChapterElement?.selectFirst("span.chapter-number")?.text() ?: ""
            val maxChapterNum = Regex("([0-9]+(\\.[0-9]+)?)").find(latestChapterName)?.value?.toFloatOrNull() ?: 0f

            val visibleChapters = document.select(chapterListSelector()).map { chapterFromElement(it) }
            chapters.addAll(visibleChapters)

            // Guess URLs for previous chapters
            val mangaSlug = manga.url.trim('/').substringAfterLast('/')
            val lowestVisible = visibleChapters.minOfOrNull { Regex("([0-9]+(\\.[0-9]+)?)").find(it.name)?.value?.toFloatOrNull() ?: 0f } ?: maxChapterNum

            var currentNum = lowestVisible.toInt() - 0
            var failCount = 0

            // Stop if 3 failures in a row
            while (currentNum >= 1 && failCount < 3) {
                val potentialUrl = "$baseUrl/chapter/$mangaSlug-chapter-$currentNum/"

                if (chapters.any { it.url.contains("chapter-$currentNum/") }) {
                    currentNum--
                    continue
                }

                try {
                    val chapRequest = GET(potentialUrl, headers)
                    val chapResponse = client.newCall(chapRequest).execute()

                    if (chapResponse.code == 200) {
                        val sChapter = SChapter.create().apply {
                            url = "/chapter/$mangaSlug-chapter-$currentNum/"
                            name = "Chapter $currentNum"
                            chapter_number = currentNum.toFloat()
                            date_upload = 0L
                        }
                        chapters.add(sChapter)
                        failCount = 0
                    } else {
                        failCount++
                    }
                    chapResponse.close()
                } catch (e: Exception) {
                    failCount++
                }
                currentNum--
            }

            chapters.sortedByDescending { Regex("([0-9]+(\\.[0-9]+)?)").find(it.name)?.value?.toFloatOrNull() ?: 0f }
        }
    }

    override fun chapterListSelector() = "ul.chapters-list li.chapter:not(.locked-chapter)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val anchor = element.selectFirst("a")!!
        setUrlWithoutDomain(anchor.attr("href"))
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
            val url = (img.attr("abs:data-src").ifBlank { img.attr("abs:src") }).trim()
            Page(index, "", url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // =========================================================================
    //  Helpers
    // =========================================================================

    private fun Response.asJsoup(): Document = Jsoup.parse(body.string(), request.url.toString())

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormatFull.parse(dateStr)?.time
                ?: dateFormatShort.parse(dateStr)?.time
                ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
