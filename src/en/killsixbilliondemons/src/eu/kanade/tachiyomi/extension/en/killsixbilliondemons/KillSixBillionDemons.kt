package eu.kanade.tachiyomi.extension.en.killsixbilliondemons

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class KillSixBillionDemons :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = false

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
    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchBooksAsMangas(), false)

    /**
     * This fetches the different books of Kill Six Billion Demons as different manga.
     * @return a list of all books in form of multiple manga
     */
    private suspend fun fetchBooksAsMangas(): List<SManga> {
        val doc = client.get(baseUrl).asJsoup()
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
                status = getStatusForBook(bookTitle, doc)
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
    private suspend fun fetchThumbnailUrl(bookOverviewUrl: String): String {
        val overviewDoc = client.get(bookOverviewUrl + PAGES_ORDER).asJsoup()
        return overviewDoc.selectFirst(".comic-thumbnail-in-archive a img")!!.attr("src")
    }

    /**
     * Get the SManga status for a given book by checking if the title of the newest page contains
     * the title of the the given book.
     *
     * @param bookTitle name of the book the status should be fetched for
     * @param newestPage the home page, which shows the newest page
     * @return the status of the book (as Enum value of SManga because chapters are mangas)
     */
    private fun getStatusForBook(bookTitle: String, newestPage: Document): Int {
        val bookTitleWithoutBook = bookTitle.substringAfter(": ")
        val postTitle = newestPage.selectFirst(".post-title")?.text() ?: ""
        // title is "<book name> <page(s)>"
        return if (postTitle.contains(bookTitleWithoutBook, ignoreCase = true)) {
            SManga.UNKNOWN
        } else {
            SManga.COMPLETED
        }
    }

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ========================= Details & Chapters =========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) fetchBooksAsMangas().find { manga.title == it.title } ?: manga else manga
        }
        val chapterList = async {
            if (fetchChapters) fetchChapterList(manga) else chapters
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val doc = client.get(baseUrl + manga.url).asJsoup()
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

        return chapters.reversed()
    }

    /**
     * Fetches all pages of the active chapter from the website, following the archive pagination,
     * creating individual chapter entries for each.
     *
     * @param chapterUrl the relative URL path of the active chapter archive
     * @param chapterTitle the title prefix of the chapter (e.g. "Chapter 6")
     * @param startChapterNumber the base chapter number (e.g. 6.0f)
     * @return a list of page-based SChapter objects
     */
    private suspend fun fetchActiveChapterPages(
        chapterUrl: String,
        chapterTitle: String,
        startChapterNumber: Float,
    ): List<SChapter> = buildList {
        var currentUrl = baseUrl + chapterUrl + PAGES_ORDER

        while (true) {
            val currentPage = client.get(currentUrl).asJsoup()

            val links = currentPage.select(".comic-thumbnail-in-archive a")
            for (link in links) {
                val href = link.attr("href")
                val title = link.attr("title").trim()
                if (href.isNotEmpty()) {
                    val pageNum = size + 1
                    val pageTitle = if (title.isNotEmpty()) "$chapterTitle - $title" else "$chapterTitle Page $pageNum"
                    add(
                        SChapter.create().apply {
                            setUrlWithoutDomain(href)
                            name = pageTitle
                            chapter_number = startChapterNumber + (pageNum / 1000f)
                            date_upload = 0L
                        },
                    )
                }
            }

            currentUrl = currentPage.select(".paginav-next a").attr("href")
            if (currentUrl.isEmpty()) break
        }
    }

    // ========================= Pages =========================

    /**
     * Collects comic page images for a chapter, following pagination. Supports both chapter
     * archives (with multiple thumbnails) and individual comic pages.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> = buildList {
        var currentUrl = baseUrl + chapter.url + PAGES_ORDER

        while (true) {
            val currentPage = client.get(currentUrl).asJsoup()

            val images = currentPage.select(".comic-thumbnail-in-archive a img")
            if (images.isNotEmpty()) {
                for (img in images) {
                    img.attr("src").takeIf { it.isNotEmpty() }?.let { src ->
                        val imageUrl = src.replace(wordpressThumbnailRegex, "")
                        add(Page(size + 1, "", imageUrl))
                    }
                }
            } else {
                currentPage.selectFirst("#comic img")?.attr("src")?.takeIf { it.isNotEmpty() }?.let { src ->
                    val imageUrl = src.replace(wordpressThumbnailRegex, "")
                    add(Page(size + 1, "", imageUrl))
                }
            }

            currentUrl = currentPage.select(".paginav-next a").attr("href")
            if (currentUrl.isEmpty()) break
        }
    }

    // ========================= Search =========================
    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = throw Exception("Search functionality is not available.")

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
