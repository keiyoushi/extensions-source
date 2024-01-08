package eu.kanade.tachiyomi.extension.en.killsixbilliondemons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class KillSixBillionDemons : HttpSource() {

    override val name = "KillSixBillionDemons"

    override val baseUrl = "https://killsixbilliondemons.com"

    override val lang = "en"

    override val supportsLatest: Boolean = false

    private val authorKSBD = "Abbadon"
    private val bookSelector: String = "#chapter option:contains(book)"
    private val pagesOrder = "?order=ASC"
    private val urlDateFormat = SimpleDateFormat("yyyy/MM", Locale.US)
    private val descriptionKSBD =
        "Q: What is this all about?\nThis is a webcomic! It’s graphic novel style, meaning it’s meant to be read in large chunks, but you can subject yourself to the agony of reading it a couple pages a week!\n" +
            "\nQ: Do you have a twitter/tumble machine? Just who the hell draws this thing anyway?\n" +
            "A mysterious comics goblin named Abbadon draws this mess. My twitter is @orbitaldropkick, my tumblr is orbitaldropkick.tumblr.com. If you’re feeling dangerous, you can e-mail me at ksbdabbadon@gmail.com\n" +
            "\nQ: A webcomic, eh? When does it update?\n" +
            "Tuesday and Friday evenings (and occasionally weekends). Sometimes it will be up quite late on those days.\n" +
            "\nQ: Who’s this YISUN guy that keeps getting talked about?\n" +
            "Someone has not read their Psalms and Spasms recently!\n" +
            "\nQ: What’s this about suggestions?\n" +
            "KSBD will periodically take suggestions, mostly on characters to stick in the background. You can also stick fanart, character ideas, concepts, and literature in the ‘Submit’ section up above. You need tumblr for this. If you want to suggest directly, the best way to do it is through the comments section below the comic! A huge chunk of minor characters have been named and inspired by reader comments so far.\n" +
            "\nQ: Can I buy this book in a more traditional format?\n" +
            "You absolutely can. You can get your hands on a print copy of the first and second books from Image comics in your local comics shop or anywhere else you can get comics. It looks fantastic in print and if you don’t like reading stuff online I highly recommend it."

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    // list of books
    override fun popularMangaParse(response: Response): MangasPage {
        return generateKSBDMangasPage()
    }

    /**
     * @return the MangasPage containing the different books of Kill Six Billion Demons as manga
     */
    private fun generateKSBDMangasPage(): MangasPage {
        return MangasPage(fetchBooksAsMangas(), false)
    }

    /**
     * This fetches the different books of Kill Six Billion Demons as different manga.
     * @return a list of all books in form of multiple manga
     */
    private fun fetchBooksAsMangas(): List<SManga> {
        val doc = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        val bookElements = doc.select(bookSelector)
        return bookElements.map { bookElement ->
            val bookOverviewUrl = bookElement.attr("value")
            val bookTitle = bookElement.text().substringBefore(" (")

            SManga.create().apply {
                title = bookTitle
                setUrlWithoutDomain(bookOverviewUrl)
                artist = authorKSBD
                author = authorKSBD
                description = descriptionKSBD
                thumbnail_url = fetchThumbnailUrl(bookOverviewUrl)
                status = fetchStatusForBook(bookTitle)
            }
        }
    }

    /**
     * This fetches the Thumbnail given the url to a book overview. In ascending order the first
     * image will always be the cover of the given book.
     *
     * @param bookOverviewUrl url to the book overview
     * @return url to the cover of the book
     */
    private fun fetchThumbnailUrl(bookOverviewUrl: String): String {
        val overviewDoc =
            client.newCall(GET(bookOverviewUrl + pagesOrder, headers)).execute().asJsoup()
        return overviewDoc.selectFirst(".comic-thumbnail-in-archive a img")!!.attr("src")
    }

    /**
     * Get the SManga status for a given book by checking if the title of the newest page contains
     * the title of the the given book.
     *
     * @param bookTitle name of the book the status should be fetched for
     * @return the status of the book (as Enum value of SManga because chapters are mangas)
     */
    private fun fetchStatusForBook(bookTitle: String): Int {
        val bookTitleWithoutBook = bookTitle.substringAfter(": ")
        val newestPage = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        val postTitle = newestPage.selectFirst(".post-title")?.text() ?: ""
        // title is "<book name> <page(s)>"
        return if (postTitle.lowercase().contains(bookTitleWithoutBook.lowercase())) {
            SManga.UNKNOWN
        } else {
            SManga.COMPLETED
        }
    }

    // latest Updates not used
    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(fetchBooksAsMangas().find { manga.title == it.title })
    }

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            fetchChapterListTR(
                baseUrl + manga.url + pagesOrder,
                mutableListOf(),
            ).reversed(),
        )
    }

    /**
     * Though this is recursive this will be optimized by the compiler into a for loop equivalent
     * thing. This has to be done this way because the maximum number of further chapter overview
     * pages that will be shown on one chapter overview page will at maximum be 5 enven though there
     * might be more.
     *
     * @param currentUrl url of the current page / the page this algorithm will start the recursion on
     * @param foundChapters a list of all found chapters (should be empty)
     * @return a list of all chapters that were found under "currentUrl" and following pages
     */
    private tailrec fun fetchChapterListTR(
        currentUrl: String,
        foundChapters: MutableList<SChapter>,
    ): MutableList<SChapter> {
        val numberOfPreviousChapters = foundChapters.size
        val currentPage = client.newCall(GET(currentUrl, headers)).execute().asJsoup()
        val chaptersOnCurrentPage = currentPage.select(".post-content")
            .mapIndexed { index, chapterElement ->
                val chapterTitle: String = chapterElement.select(".post-title a").text()
                val chapterUrl: String =
                    chapterElement.select(".comic-thumbnail-in-archive a").attr("href")
                val imageUrl =
                    chapterElement.select(".comic-thumbnail-in-archive a img").attr("src")

                SChapter.create().apply {
                    setUrlWithoutDomain(chapterUrl)
                    name = chapterTitle
                    chapter_number = numberOfPreviousChapters + index + 1f
                    date_upload = extractDateFromImageUrl(imageUrl)
                }
            }

        foundChapters.addAll(chaptersOnCurrentPage)

        val potentialNextPageUrl = currentPage.select(".paginav-next a").attr("href")
        return if (potentialNextPageUrl.isEmpty()) {
            foundChapters
        } else {
            fetchChapterListTR(potentialNextPageUrl, foundChapters)
        }
    }

    /**
     * @param imageUrl Url the date should be got from
     * @return date of the image upload as a long
     */
    private fun extractDateFromImageUrl(imageUrl: String): Long {
        val dateRegex = "[0-9]{4}/[0-9]{2}".toRegex()
        val dateString = dateRegex.find(imageUrl)
        return if (dateString?.value != null) {
            return urlDateFormat.parse(dateString.value)?.time ?: 0L
        } else {
            0L
        }
    }

    override fun chapterListRequest(manga: SManga): Request = throw Exception("Not used")

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterDoc = client.newCall(GET(baseUrl + chapter.url, headers)).execute().asJsoup()
        val pages = chapterDoc.select("#comic img")
            .mapIndexed { index, imageElement ->
                val imageUrl = imageElement.attr("src")
                Page(index + 1, "", imageUrl)
            }

        return Observable.just(pages)
    }

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = throw Exception("Search functionality is not available.")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw Exception("Not used")
}
