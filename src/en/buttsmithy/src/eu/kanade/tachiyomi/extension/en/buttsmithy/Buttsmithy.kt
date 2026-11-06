package eu.kanade.tachiyomi.extension.en.buttsmithy

import android.app.Application
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.OkHttpClient
import org.jsoup.select.Elements
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

@Source
abstract class Buttsmithy : KeiSource() {

    // the full version of alfie for some reason has a separate url and isn't accessed like the other comics
    private val baseUrlAlfie = "https://buttsmithy.com"

    private val inCase = "InCase"
    private val alfieTitle = "Alfie"
    private val chapterNumberRegex = Regex("""\d+(?:\.\d+)?""")
    private val imageSizeSuffixRegex = Regex("""-\d+x\d+(?=\.\w+$)""")

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(TextInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchAllComics(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = when {
        manga.url.startsWith(baseUrlAlfie) -> SMangaUpdate(alfieManga(), fetchAlfieChapters())
        fetchChapters -> SMangaUpdate(manga, fetchOtherPagesAsChapters(manga.title, baseUrl + manga.url).reversed())
        else -> SMangaUpdate(manga, chapters)
    }

    // Alfie manga and all chapters store absolute urls
    override fun getMangaUrl(manga: SManga): String = if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith(baseUrlAlfie)) return fetchAlfiePages(chapter.url)

        val comicPageDoc = client.get(chapter.url, Fetch.DOCUMENT).asJsoup()
        val imageUrl = comicPageDoc.select("#comic img").attr("src")

        return listOf(Page(0, imageUrl = imageUrl))
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
    private tailrec suspend fun fetchOtherPagesAsChapters(
        comicTitle: String,
        currentPageUrl: String,
        pageNr: Float = 0f,
        allChapters: MutableList<SChapter> = mutableListOf(),
    ): MutableList<SChapter> {
        val currentDoc = client.get(currentPageUrl, Fetch.DOCUMENT).asJsoup()
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

    private suspend fun fetchAlfieChapters(): List<SChapter> {
        val document = client.get(baseUrlAlfie, Fetch.DOCUMENT).asJsoup()
        return document.select("#chapter option.level-0")
            .map { option ->
                SChapter.create().apply {
                    url = option.absUrl("value")
                    name = option.text()
                }
            }
            // the site lists its chapters out of order; Misc (holiday extras) goes on top
            .sortedByDescending { chapterNumberRegex.find(it.name)?.value?.toFloat() ?: Float.MAX_VALUE }
    }

    // Chapter archives list 10 comics per page, newest first; wide images there are WordPress's resized copy
    private suspend fun fetchAlfiePages(firstComicUrl: String): List<Page> {
        val firstComic = client.get(firstComicUrl, Fetch.DOCUMENT).asJsoup()
        var archiveUrl = firstComic.selectFirst(".comic-chapter a")!!.absUrl("href")
        val imageUrls = mutableListOf<String>()
        while (archiveUrl.isNotEmpty()) {
            val archive = client.get(archiveUrl, Fetch.DOCUMENT).asJsoup()
            archive.select(".comic-thumbnail-in-archive img").mapTo(imageUrls) {
                it.absUrl("src").replace(imageSizeSuffixRegex, "")
            }
            archiveUrl = archive.selectFirst(".paginav-next a")?.absUrl("href").orEmpty()
        }
        return imageUrls.asReversed().mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    /**
     * Fetches all comics that are currently hosted on buttsmithy (including the first version of
     * alfie currently)
     *
     * @return a list of all comics currently hosted on buttsmithy
     */
    private suspend fun fetchAllComics(): List<SManga> {
        val mainDoc = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        // Incases choose your own adventure comics
        val cyoaSelector = "#menu-item-331"
        // Incase other comics (ignoring alfie because alfie has its own subdomain)
        val otherComicsSelector = "#menu-item-38"

        val cyoaComics = convertMenuElementToSManga(mainDoc.select(cyoaSelector))
        val otherComics = convertMenuElementToSManga(mainDoc.select(otherComicsSelector))

        // concat all different comic lists
        return listOf(listOf(alfieManga()), cyoaComics, otherComics).flatten()
    }

    private fun String.lowercase(): String = this.lowercase(Locale.getDefault())

    private fun alfieManga() = SManga.create().apply {
        url = baseUrlAlfie
        title = alfieTitle
        author = inCase
        artist = inCase
        genre = "fantasy, NSFW"
        thumbnail_url = generateImageUrlWithText(alfieTitle)
    }

    private fun convertMenuElementToSManga(menuElement: Elements): List<SManga> {
        val comicLinkSelector = ".menu-item-type-custom a[href]"

        val linkElements = menuElement.select(comicLinkSelector)
        return linkElements
            // filter out the first Alfie chapter that is still hosted under "incase.buttsmithy.com"
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

    private fun generateImageUrlWithText(text: String): String = TextInterceptorHelper.createUrl(text, "")
}
