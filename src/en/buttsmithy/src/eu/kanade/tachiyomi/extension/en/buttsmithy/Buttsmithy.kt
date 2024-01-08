package eu.kanade.tachiyomi.extension.en.buttsmithy

import android.app.Application
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
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale

class Buttsmithy : HttpSource() {

    override val name = "Buttsmithy"

    override val baseUrl = "https://incase.buttsmithy.com"

    // the full version of alfie for some reason has a separate url and isn't accessed like the other comics
    private val baseUrlAlfie = "https://buttsmithy.com"
    private val chapterOverviewBaseUrl = "$baseUrlAlfie/archives/chapter"

    override val lang = "en"

    private val inCase = "InCase"
    private val alfieTitle = "Alfie"
    private val alfieDateParser = SimpleDateFormat("HH:mm MMMM dd, yyyy", Locale.US)

    override val supportsLatest: Boolean = false

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters: List<SChapter> =
            if (manga.title.contains(alfieTitle)) {
                // TODO misc-chapter is currently broken
                fetchAlfiePagesAsChapters(manga.url).reversed()
            } else {
                fetchOtherPagesAsChapters(manga.title, baseUrl + manga.url).reversed()
            }
        return Observable.just(chapters)
    }

    /**
     * Fetches all the pages from a Comic and returns them as separate SChapters.
     * Because there is no overview for comics that aren't Alfie this needs to visit each page of
     * the chosen comic.
     *
     * @param comicTitle title of the comic that is fetched (is needed for setting the date only once)
     * @param currentPageUrl url of the current page the function will start / continue the recursion on
     * @param allChapters list of all chapters (should be initialized with an empty list)
     * @return returns a list of all pages that were found as SChapters
     * */
    private tailrec fun fetchOtherPagesAsChapters(
        comicTitle: String,
        currentPageUrl: String,
        pageNr: Float = 0f,
        allChapters: MutableList<SChapter> = mutableListOf(),
    ): MutableList<SChapter> {
        val currentDoc = client.newCall(GET(currentPageUrl, headers)).execute().asJsoup()
        val currentPageComicPage = currentDoc.select("#comic img").first()!!
        val chapterTitle = currentPageComicPage.attr("alt")

        val chapter = SChapter.create().apply {
            /* the setUrlWithoutDomain method can't be used here because Alfie has another base
             * namespace and when retrieving the pages it is impossible to clearly differentiate an
             * Alfie Chapter from some other comic chapter. */
            url = currentPageUrl
            name = chapterTitle
            chapter_number = pageNr
        }

        // get the preferences for the current comic
        val seriesPrefs = Injekt.get<Application>().getSharedPreferences("source_${id}_updateTime:${comicTitle.lowercase()}", 0)
        val seriesPrefEditor = seriesPrefs.edit()

        val currentTimeMillis = System.currentTimeMillis()
        // only update the time if the current chapter is not downloaded yet
        if (!seriesPrefs.contains(chapter.name)) {
            seriesPrefEditor.putLong(chapter.name, currentTimeMillis)
        }
        chapter.date_upload = seriesPrefs.getLong(chapter.name, currentTimeMillis)

        seriesPrefEditor.apply()

        allChapters.add(chapter)

        val potentialNextPageUrl = currentDoc.select(".comic-nav-next").attr("href")

        return if (potentialNextPageUrl.isEmpty()) {
            allChapters
        } else {
            fetchOtherPagesAsChapters(comicTitle, potentialNextPageUrl, pageNr + 1, allChapters)
        }
    }

    /**
     * Fetches all the pages from one of Alfies chapters and returns them as separate SChapters.
     *
     * @param currentPageUrl url of the current page the function will start / continue the recursion on
     * @param lastPageNr page Number the function start enumeration on if no page number can be extracted from the title
     * @param allChapters list of all chapters (should be initialized with an empty list)
     * @return returns a list of all pages that were found in the chapter overview as SChapters
     * */
    private tailrec fun fetchAlfiePagesAsChapters(
        currentPageUrl: String,
        lastPageNr: Float = 0f,
        allChapters: MutableList<SChapter> = mutableListOf(),
    ): MutableList<SChapter> {
        val pageNrRegex = "p*[0-9]+".toRegex()

        val currentDoc = client.newCall(GET(currentPageUrl, headers)).execute().asJsoup()
        val pagesAsChapters = currentDoc.select("article.has-post-thumbnail .post-content")
            .mapIndexed { index, postElement ->
                val postTitleElement = postElement.select(".post-info .post-title a")
                val chapUrl = postTitleElement.attr("href")
                val title = postTitleElement.text()
                // this is needed for the MISC chapter where the pages are not numbered
                val pageNr =
                    if (pageNrRegex.matches(title)) {
                        title.substringAfter("p").trim().toFloat()
                    } else {
                        index + lastPageNr
                    }

                val dateString = postElement.select(".post-info .post-date").text()
                val timeString = postElement.select(".post-info .post-time").text()
                val date = alfieDateParser.parse("$timeString $dateString")?.time ?: 0L

                SChapter.create().apply {
                    /* Alfie has its own name space and thus can't be handled like other comics.
                     * This means the setUrlWithoutDomain method can't be used */
                    url = chapUrl
                    name = title
                    chapter_number = pageNr
                    date_upload = date
                }
            }

        allChapters.addAll(pagesAsChapters)

        val potentialNextPageUrl = currentDoc.select(".paginav-next a").attr("href")
        return if (potentialNextPageUrl.isEmpty()) {
            allChapters
        } else {
            fetchAlfiePagesAsChapters(potentialNextPageUrl, lastPageNr, allChapters)
        }
    }

    /**
     * Fetches all comics that are currently hosted on buttsmithy (including the first version of
     * alfie currently)
     *
     * @return a list of all comics currently hosted on buttsmithy with alfies chapters separated into separate mangas
     */
    private fun fetchAllComics(): List<SManga> {
        val mainDoc = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        // Incases choose your own adventure comics
        val cyoaSelector = "#menu-item-331"
        // Incase other comics (ignoring alfie because alfie has its own subdomain)
        val otherComicsSelector = "#menu-item-38"

        val alfieChapters = fetchAlfieSMangas()
        val cyoaComics = convertMenuElementToSManga(mainDoc.select(cyoaSelector))
        val otherComics = convertMenuElementToSManga(mainDoc.select(otherComicsSelector))

        // concat all different comic lists
        return listOf(alfieChapters, cyoaComics, otherComics).flatten()
    }

    private fun String.lowercase(): String {
        return this.lowercase(Locale.getDefault())
    }

    /**
     * Fetches all chapters of Alfie (one of InCases comics) as separate SManga because this comic
     * is gigantic and only updates one page at a time. Putting the pages into SChapters would block
     * the automatic update fetching.
     *
     * @return all of Alfies chapters as separate SManga
     */
    private fun fetchAlfieSMangas(): List<SManga> {
        val pageDoc = client.newCall(GET(baseUrlAlfie, headers)).execute().asJsoup()
        val mostRecentChapTitle = extractChapterTitleFromPageDoc(pageDoc)

        val chaptersAsSManga: List<SManga> =
            pageDoc.select("#chapter").select("option.level-0")
                .map { chapterElement ->
                    val chapTitle = chapterElement.text().lowercase()
                    val chapUrlName = chapterTitleToChapterUrlName(chapTitle)

                    SManga.create().apply {
                        url = "$chapterOverviewBaseUrl/$chapUrlName"
                        title = "$alfieTitle - $chapTitle"
                        author = inCase
                        artist = inCase
                        status = decideAlfieStatusFromTitle(chapTitle, mostRecentChapTitle)
                        genre = "fantasy, NSFW"
                        thumbnail_url = generateImageUrlWithText(alfieTitle)
                    }
                }

        return chaptersAsSManga
    }

    private fun decideAlfieStatusFromTitle(chapTitle: String, mostRecentChapTitle: String): Int {
        return if (chapTitle == mostRecentChapTitle) {
            SManga.UNKNOWN
        } else {
            SManga.COMPLETED
        }
    }

    private fun extractChapterTitleFromPageDoc(doc: Document): String {
        return doc.select(".comic-chapter a").first()!!.text().lowercase()
    }

    private fun chapterTitleToChapterUrlName(chapTitle: String): String {
        return when (chapTitle.lowercase()) {
            "chapter 1" -> "chapter-1v2"
            else -> chapTitle.replace(" ", "-").replace(".", "-")
        }
    }

    private fun convertMenuElementToSManga(menuElement: Elements): List<SManga> {
        val comicLinkSelector = ".menu-item-type-custom a[href]"

        val linkElements = menuElement.select(comicLinkSelector)
        return linkElements
            // filter out the first Alfie chapter that is still hosted under "incase.buttsmithy.com"
            // see "fetchAlfieSMangas()" for how Alfie should be retrieved
            .filter { linkElement -> !linkElement.text().contains(alfieTitle) }
            .map { linkElement ->
                val comicTitle = linkElement.text()
                val comicUrl = linkElement.attr("href")

                SManga.create().apply {
                    setUrlWithoutDomain(comicUrl)
                    title = comicTitle
                    author = inCase
                    artist = inCase
                    status = SManga.COMPLETED
                    genre = "NSFW"
                    thumbnail_url = generateImageUrlWithText(comicTitle)
                    status = SManga.COMPLETED
                }
            }
    }

    private fun generateImageUrlWithText(text: String): String {
        return "https://fakeimg.pl/800x1236/?text=$text&font=lobster"
    }

    private fun generateMangasPage(): MangasPage {
        return MangasPage(fetchAllComics(), false)
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    override fun imageUrlParse(response: Response): String {
        val pageDoc = response.asJsoup()
        return pageDoc.select("#comic").select("img[src]").attr("href")
    }

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(
            if (manga.title.contains(alfieTitle)) {
                val pageDoc = client.newCall(GET(baseUrlAlfie, headers)).execute().asJsoup()
                val mostRecentChapTitle = extractChapterTitleFromPageDoc(pageDoc)
                val chapTitle = manga.title.substringAfter("Alfie - ").trim()

                SManga.create().apply {
                    url = "$chapterOverviewBaseUrl/${chapterTitleToChapterUrlName(chapTitle)}"
                    title = "$alfieTitle - $chapTitle"
                    author = inCase
                    artist = inCase
                    status = decideAlfieStatusFromTitle(chapTitle, mostRecentChapTitle)
                    genre = "fantasy, NSFW"
                    thumbnail_url = generateImageUrlWithText(alfieTitle)
                }
            } else {
                manga
            },
        )
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val comicPageDoc = client.newCall(GET(chapter.url, headers)).execute().asJsoup()
        val imageUrl = comicPageDoc.select("#comic img").attr("src")
        val comicPage = Page(0, "", imageUrl)

        return Observable.just(listOf(comicPage))
    }

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(generateMangasPage())
    }

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw Exception("Not used")
}
