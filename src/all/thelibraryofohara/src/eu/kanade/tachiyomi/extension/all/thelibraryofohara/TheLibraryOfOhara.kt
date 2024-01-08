package eu.kanade.tachiyomi.extension.all.thelibraryofohara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class TheLibraryOfOhara(override val lang: String, private val siteLang: String) : ParsedHttpSource() {

    override val name = "The Library of Ohara"

    override val baseUrl = "https://thelibraryofohara.com"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    // only show entries which contain pictures only.
    override fun popularMangaSelector() = when (lang) {
        "en" ->
            "#categories-7 ul li.cat-item-589813936," + // Chapter Secrets
                "#categories-7 ul li.cat-item-607613583, " + // Chapter Secrets Specials
                "#categories-7 ul li.cat-item-43972770, " + // Charlotte Family
                "#categories-7 ul li.cat-item-9363667, " + // Complete Guides
                "#categories-7 ul li.cat-item-634609261, " + // Parody Chapter
                "#categories-7 ul li.cat-item-699200615, " + // Return to the Reverie
                "#categories-7 ul li.cat-item-139757, " + // SBS
                "#categories-7 ul li.cat-item-22695, " + // Timeline
                "#categories-7 ul li.cat-item-648324575" // Vivre Card Databook
        "id" -> "#categories-7 ul li.cat-item-702404482, #categories-7 ul li.cat-item-699200615" // Chapter Secrets Bahasa Indonesia, Return to the Reverie
        "fr" -> "#categories-7 ul li.cat-item-699200615" // Return to the Reverie
        "ar" -> "#categories-7 ul li.cat-item-699200615" // Return to the Reverie
        "it" -> "#categories-7 ul li.cat-item-699200615" // Return to the Reverie
        else -> "#categories-7 ul li.cat-item-693784776, #categories-7 ul li.cat-item-699200615" // Chapter Secrets (multilingual), Return to the Reverie
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.title = element.select("a").text()
        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        return MangasPage(popularMangaParse(response).mangas.filter { it.title.contains(query, ignoreCase = true) }, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("h1.page-title").text().replace("Category: ", "")
        manga.thumbnail_url = chooseChapterThumbnail(document, manga.title)
        manga.description = ""
        manga.status = SManga.ONGOING
        return manga
    }

    // Use one of the chapter thumbnails as manga thumbnail
    // Some thumbnails have a flag on them which indicates the Language.
    // Try to choose a thumbnail with a matching flag
    private fun chooseChapterThumbnail(document: Document, mangaTitle: String): String? {
        var imgElement: Element? = null

        // Reverie
        if (mangaTitle.contains("Reverie")) {
            imgElement = document.select("article").firstOrNull { element ->
                val chapterTitle = element.select("h2.entry-title a").text()
                (chapterTitle.contains(siteLang) || (lang == "en" && !chapterTitle.contains(Regex("""(French|Arabic|Italian|Indonesia|Spanish)"""))))
            }
        }
        // Chapter Secrets (multilingual)
        if (mangaTitle.contains("Chapter Secrets") && lang != "en") {
            imgElement = document.select("article").firstOrNull {
                val chapterTitle = it.select("h2.entry-title a").text()
                ((lang == "id" && chapterTitle.contains("Indonesia")) || (lang == "es" && !chapterTitle.contains("Indonesia")))
            }
        }

        // Fallback
        imgElement = imgElement ?: document.select("article:first-of-type").firstOrNull()
        return imgElement?.select("img")?.attr("abs:src")
    }

    // Chapters

    override fun chapterListSelector() = "article"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.select("a.entry-thumbnail").attr("href"))
        chapter.name = element.select("h2.entry-title a").text()
        chapter.date_upload = parseChapterDate(element.select("span.posted-on time").attr("datetime"))

        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val parsedDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).parse(date.replace("+00:00", "+0000"))
        return parsedDate?.time ?: 0L
    }

    private fun chapterNextPageSelector() = "div.nav-previous a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var document = response.asJsoup()

        while (true) {
            val pageChapters = document.select(chapterListSelector()).map { chapterFromElement(it) }
            if (pageChapters.isEmpty()) {
                break
            }

            allChapters += pageChapters

            val hasNextPage = document.select(chapterNextPageSelector()).isNotEmpty()
            if (!hasNextPage) {
                break
            }

            val nextUrl = document.select(chapterNextPageSelector()).attr("href")
            document = client.newCall(GET(nextUrl, headers)).execute().asJsoup()
        }

        if (allChapters.isNotEmpty() && allChapters[0].name.contains("Reverie")) {
            return when (lang) {
                "fr" -> allChapters.filter { it.name.contains("French") }.toMutableList()
                "ar" -> allChapters.filter { it.name.contains("Arabic") }.toMutableList()
                "it" -> allChapters.filter { it.name.contains("Italian") }.toMutableList()
                "id" -> allChapters.filter { it.name.contains("Indonesia") }.toMutableList()
                "es" -> allChapters.filter { it.name.contains("Spanish") }.toMutableList()
                else -> allChapters.filter { // english
                    !it.name.contains("French") &&
                        !it.name.contains("Arabic") &&
                        !it.name.contains("Italian") &&
                        !it.name.contains("Indonesia") &&
                        !it.name.contains("Spanish")
                }.toMutableList()
            }
        }

        // Remove Indonesian posts if lang is spanish
        // Indonesian and Spanish posts are mixed in the same category "multilingual" on the website
        // BTW, the same problem doesn't apply if lang is Indonesian because Indonesian has its own category
        if (lang == "es") {
            return allChapters.filter { !it.name.contains("Indonesia") }.toMutableList()
        }

        return allChapters
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.entry-content").select("a img, img.size-full").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("data-orig-file")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")
}
