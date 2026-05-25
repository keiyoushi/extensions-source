package eu.kanade.tachiyomi.extension.en.killsixbilliondemons

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class KillSixBillionDemons :
    HttpSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val name = "KillSixBillionDemons"

    override val baseUrl = "https://killsixbilliondemons.com"

    override val lang = "en"

    override val supportsLatest: Boolean = false

    private val descriptionKSBD = """
        Q: What is this all about?
        This is a webcomic! It’s graphic novel style, meaning it’s meant to be read in large chunks, but you can subject yourself to the agony of reading it a couple pages a week!

        Q: Do you have a twitter/tumble machine? Just who the hell draws this thing anyway?
        A mysterious comics goblin named Abbadon draws this mess. My twitter is @orbitaldropkick, my tumblr is orbitaldropkick.tumblr.com. If you’re feeling dangerous, you can e-mail me at ksbdabbadon@gmail.com

        Q: A webcomic, eh? When does it update?
        Tuesday and Friday evenings (and occasionally weekends). Sometimes it will be up quite late on those days.

        Q: Who’s this YISUN guy that keeps getting talked about?
        Someone has not read their Psalms and Spasms recently!

        Q: What’s this about suggestions?
        KSBD will periodically take suggestions, mostly on characters to stick in the background. You can also stick fanart, character ideas, concepts, and literature in the ‘Submit’ section up above. You need tumblr for this. If you want to suggest directly, the best way to do it is through the comments section below the comic! A huge chunk of minor characters have been named and inspired by reader comments so far.

        Q: Can I buy this book in a more traditional format?
        You absolutely can. You can get your hands on a print copy of the first and second books from Image comics in your local comics shop or anywhere else you can get comics. It looks fantastic in print and if you don’t like reading stuff online I highly recommend it.
    """.trimIndent()

    private fun getUrlPath(url: String): String = runCatching {
        java.net.URI(url).path.takeUnless { it.isNullOrEmpty() }
    }.getOrNull() ?: url

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage = generateKSBDMangasPage()

    /**
     * @return the MangasPage containing the different books of Kill Six Billion Demons as manga
     */
    private fun generateKSBDMangasPage(): MangasPage = MangasPage(fetchBooksAsMangas(), false)

    /**
     * This fetches the different books of Kill Six Billion Demons as different manga.
     * @return a list of all books in form of multiple manga
     */
    private fun fetchBooksAsMangas(): List<SManga> {
        val doc = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        val bookElements = doc.select("#chapter option").filter { it.isBookOption }
        return bookElements.map { bookElement ->
            val bookOverviewUrl = bookElement.attr("value")
            val bookTitle = bookElement.text().substringBefore(" (")

            SManga.create().apply {
                title = bookTitle
                setUrlWithoutDomain(bookOverviewUrl)
                artist = AUTHOR_KSBD
                author = AUTHOR_KSBD
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
            client.newCall(GET(bookOverviewUrl + PAGES_ORDER, headers)).execute().asJsoup()
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
        return if (postTitle.contains(bookTitleWithoutBook, ignoreCase = true)) {
            SManga.UNKNOWN
        } else {
            SManga.COMPLETED
        }
    }

    // ========================= Latest =========================
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    // ========================= Details =========================
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(fetchBooksAsMangas().find { manga.title == it.title })

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // ========================= Chapters =========================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val doc = client.newCall(GET(baseUrl + manga.url, headers)).execute().asJsoup()
        val options = doc.select("#chapter option")

        val allOptions = options.filter { it.isValidOption }

        val groupEnded = preferences.getBoolean(ACTIVE_CHAPTER_PREF_KEY, false)

        val lastChapterIndex = allOptions.indexOfLast { !it.isBookOption }

        val chapters = buildList {
            var foundBook = false
            var chapterIndex = 1f

            for ((i, option) in allOptions.withIndex()) {
                val text = option.text().trim()
                val value = option.attr("value")

                if (option.isBookOption) {
                    if (foundBook) {
                        // Reached the next book, stop gathering chapters
                        break
                    }
                    val optionPath = getUrlPath(value).removeSuffix("/")
                    val mangaPath = getUrlPath(manga.url).removeSuffix("/")
                    if (optionPath.equals(mangaPath, ignoreCase = true)) {
                        foundBook = true
                    }
                } else if (foundBook) {
                    val chapterTitle = "Chapter ${text.substringBefore(" (").trim()}"
                    val shouldExpand = !groupEnded || (i == lastChapterIndex)

                    if (shouldExpand) {
                        addAll(fetchActiveChapterPages(getUrlPath(value), chapterTitle, chapterIndex++))
                    } else {
                        add(
                            SChapter.create().apply {
                                setUrlWithoutDomain(value)
                                name = chapterTitle
                                chapter_number = chapterIndex++
                                date_upload = 0L
                            },
                        )
                    }
                }
            }
        }

        return Observable.just(chapters.reversed())
    }

    /**
     * Fetches all pages of the active chapter from the website, creating individual chapter entries for each.
     *
     * @param chapterUrl the relative URL path of the active chapter archive
     * @param chapterTitle the title prefix of the chapter (e.g. "Chapter 6")
     * @param startChapterNumber the base chapter number (e.g. 6.0f)
     * @return a list of page-based SChapter objects
     */
    private fun fetchActiveChapterPages(
        chapterUrl: String,
        chapterTitle: String,
        startChapterNumber: Float,
    ): List<SChapter> = buildList {
        fetchActiveChapterPagesTR(baseUrl + chapterUrl + PAGES_ORDER, chapterTitle, startChapterNumber, this)
    }

    /**
     * Recursively fetches and collects pages from active chapter archive pagination.
     *
     * @param currentUrl the current paginated URL to scrape
     * @param chapterTitle the title prefix of the chapter
     * @param startChapterNumber the base chapter number
     * @param pages mutable list where found page-chapters are accumulated
     */
    private tailrec fun fetchActiveChapterPagesTR(
        currentUrl: String,
        chapterTitle: String,
        startChapterNumber: Float,
        pages: MutableList<SChapter>,
    ) {
        val currentPage = client.newCall(GET(currentUrl, headers)).execute().asJsoup()

        val links = currentPage.select(".comic-thumbnail-in-archive a")
        for (link in links) {
            val href = link.attr("href")
            val title = link.attr("title").trim()
            if (href.isNotEmpty()) {
                val pageNum = pages.size + 1
                val pageTitle = if (title.isNotEmpty()) "$chapterTitle - $title" else "$chapterTitle Page $pageNum"
                pages.add(
                    SChapter.create().apply {
                        setUrlWithoutDomain(href)
                        name = pageTitle
                        chapter_number = startChapterNumber + (pageNum / 1000f)
                        date_upload = 0L
                    },
                )
            }
        }

        val potentialNextPageUrl = currentPage.select(".paginav-next a").attr("href")
        if (potentialNextPageUrl.isNotEmpty()) {
            fetchActiveChapterPagesTR(potentialNextPageUrl, chapterTitle, startChapterNumber, pages)
        }
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // ========================= Pages =========================
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.just(
        buildList {
            fetchPagesTR(baseUrl + chapter.url + PAGES_ORDER, this)
        },
    )

    /**
     * Recursively fetches and collects comic page images for a chapter. Supports both chapter
     * archives (with multiple thumbnails) and individual comic pages.
     *
     * @param currentUrl the current URL to scrape
     * @param pages mutable list where found Page objects are accumulated
     */
    private tailrec fun fetchPagesTR(
        currentUrl: String,
        pages: MutableList<Page>,
    ) {
        val currentPage = client.newCall(GET(currentUrl, headers)).execute().asJsoup()

        val images = currentPage.select(".comic-thumbnail-in-archive a img")
        if (images.isNotEmpty()) {
            for (img in images) {
                img.attr("src").takeIf { it.isNotEmpty() }?.let { src ->
                    val imageUrl = src.replace(wordpressThumbnailRegex, "")
                    pages.add(Page(pages.size + 1, "", imageUrl))
                }
            }
        } else {
            currentPage.selectFirst("#comic img")?.attr("src")?.takeIf { it.isNotEmpty() }?.let { src ->
                val imageUrl = src.replace(wordpressThumbnailRegex, "")
                pages.add(Page(pages.size + 1, "", imageUrl))
            }
        }

        val potentialNextPageUrl = currentPage.select(".paginav-next a").attr("href")
        if (potentialNextPageUrl.isNotEmpty()) {
            fetchPagesTR(potentialNextPageUrl, pages)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    // ========================= Search =========================
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = throw Exception("Search functionality is not available.")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    // ========================= Preferences =========================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val activeChapterPref = SwitchPreferenceCompat(screen.context).apply {
            key = ACTIVE_CHAPTER_PREF_KEY
            title = "Group ended chapters"
            summary =
                "Group pages into chapters when they have fully ended. For the active/ongoing chapter, its pages will be listed individually."
            setDefaultValue(false)
        }
        screen.addPreference(activeChapterPref)
    }

    // ========================= Helpers =========================
    private val Element.isValidOption: Boolean
        get() {
            val text = text().trim()
            return attr("value") != "0" && !text.equals("select chapter", ignoreCase = true)
        }

    private val Element.isBookOption: Boolean
        get() = isValidOption && text().trim().substringBefore(" (").trim().toFloatOrNull() == null

    companion object {
        private const val ACTIVE_CHAPTER_PREF_KEY = "group_ended"
        private const val AUTHOR_KSBD = "Abbadon"
        private const val PAGES_ORDER = "?order=ASC"
        private val wordpressThumbnailRegex = "-\\d+x\\d+(?=\\.(?:jpe?g|png|webp|gif)(?:\\?.*)?$)".toRegex(RegexOption.IGNORE_CASE)
    }
}
