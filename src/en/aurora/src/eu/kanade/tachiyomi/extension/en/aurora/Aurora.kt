package eu.kanade.tachiyomi.extension.en.aurora

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
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale

class Aurora : HttpSource() {

    override val name = "Aurora"
    override val baseUrl = "https://comicaurora.com"
    override val lang = "en"
    override val supportsLatest = false
    private val authorName = "OSP-Red"
    private val auroraGenre = "fantasy"
    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

    override fun chapterListRequest(manga: SManga): Request = throw Exception("Not used")

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(fetchChapterListTR(baseUrl + manga.url, mutableListOf()).reversed())
    }

    private tailrec fun fetchChapterListTR(
        currentUrl: String,
        foundChapters: MutableList<SChapter>,
    ): MutableList<SChapter> {
        val currentPage = client.newCall(GET(currentUrl, headers)).execute().asJsoup()

        val pagesAsChapters = currentPage.select(".post-content")
            .map { postContent ->
                val chapterUrl = postContent.select("a.webcomic-link").attr("href")
                val title = postContent.select(".post-title a").text()
                val chapterNr = title.substringAfter('.').toFloat()
                val dateString = postContent.select(".post-date").text()
                val date = dateFormat.parse(dateString)?.time ?: 0L

                SChapter.create().apply {
                    setUrlWithoutDomain(chapterUrl)
                    name = title
                    chapter_number = chapterNr
                    date_upload = date
                }
            }

        foundChapters.addAll(pagesAsChapters)

        // get a potential next page of the chapter overview
        val nextPageNavUrl = currentPage.selectFirst(".paginav-next a")?.attr("href")
        // check if a next page actually exits and if not exit
        return if (nextPageNavUrl == null) {
            foundChapters
        } else {
            fetchChapterListTR(nextPageNavUrl, foundChapters)
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.imageUrl)
    }

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val chapterNr = manga.title.substringAfter(' ').toFloatOrNull() ?: 0f

        val updatedManga = SManga.create().apply {
            setUrlWithoutDomain(manga.url)
            title = manga.title
            artist = authorName
            author = authorName
            description = auroraDescription
            genre = auroraGenre
            status = getChapterStatusForChapter(chapterNr)
            thumbnail_url = manga.thumbnail_url
        }
        return Observable.just(updatedManga)
    }

    /**
     * @param chapter chapter the status should be fetched for
     * @return the status of the chapter (as Enum value of SManga because chapters are mangas)
     */
    private fun getChapterStatusForChapter(chapter: Float): Int {
        val newestPage = client.newCall(GET(baseUrl)).execute().asJsoup()
        val postTitle = newestPage.selectFirst(".post-title")!!.text()
        // title is "<arc>.<chapter>.<page>"
        val chapterOfNewestPage = postTitle.split(".")[1].toFloat()
        return if (chapter >= chapterOfNewestPage) SManga.UNKNOWN else SManga.COMPLETED
    }

    private val auroraDescription = """
    Aurora is a fantasy webcomic (updates M/W/F) written and illustrated by Red, better known for her work on the YouTube channel “Overly Sarcastic Productions.” It’s been in the works for over a decade, and she’s finally decided to stop putting it off.

    If you’d like to discuss the comic, it now has a subreddit, as well as a dedicated twitter and a tumblr where you can ask questions. There’s also a dedicated room on the channel discord for conversations about it!

    Find Red’s general ramblings on Twitter, alongside her cohost Blue, at OSPYouTube.
    """.trimIndent()

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val singlePageChapterDoc = client.newCall(
            GET(baseUrl + chapter.url, headers),
        ).execute().asJsoup()
        val imageUrl = singlePageChapterDoc.selectFirst(
            ".webcomic-media .webcomic-link .attachment-full",
        )!!.attr("src")
        val singlePageChapter = Page(0, "", imageUrl)

        return Observable.just(listOf(singlePageChapter))
    }

    override fun pageListRequest(chapter: SChapter): Request = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    /**
     * Because the comic is updated 1 page at a time the chapters are turned into different mangas
     * so that the pages can be turned into different chapters which can be automatically updated by
     * Tachiyomi.
     *
     * @return List of all Chapters as separate mangas
     */
    private fun fetchChaptersAsMangas(): List<SManga> {
        val descriptionText = auroraDescription

        val chapterArchiveUrl = "$baseUrl/archive/"

        val chapterOverviewDoc = client.newCall(GET(chapterArchiveUrl, headers)).execute().asJsoup()
        val chapterBlockElements = chapterOverviewDoc.select(".wp-block-image:has(a)")
        val mangasFromChapters: List<SManga> = chapterBlockElements
            .mapIndexed { chapterIndex, chapter ->
                val chapterOverviewLink = chapter.selectFirst("a")!!
                val chapterOverviewUrl = chapterOverviewLink.attr("href")
                val chapterTitle = "$name - ${chapterOverviewLink.text()}"
                val chapterThumbnail = chapter.selectFirst("img")!!.attr("src")

                SManga.create().apply {
                    setUrlWithoutDomain(chapterOverviewUrl)
                    title = chapterTitle
                    author = authorName
                    artist = authorName
                    description = descriptionText
                    genre = auroraGenre
                    // this will mark every chapter except the last one as completed
                    status =
                        if (chapterIndex >= chapterBlockElements.size - 1) {
                            SManga.UNKNOWN
                        } else {
                            SManga.COMPLETED
                        }
                    thumbnail_url = chapterThumbnail
                }
            }

        return mangasFromChapters
    }

    /**
     * Turn the list of chapters as mangas into the mangas page that can be returned for every
     * request.
     */
    private fun generateAuroraMangasPage(): MangasPage {
        return MangasPage(fetchChaptersAsMangas(), false)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(generateAuroraMangasPage())
    }

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")
}
