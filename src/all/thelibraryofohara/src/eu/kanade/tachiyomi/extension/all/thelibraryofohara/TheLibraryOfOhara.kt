package eu.kanade.tachiyomi.extension.all.thelibraryofohara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class TheLibraryOfOhara(override val lang: String, private val siteLang: String) : HttpSource() {

    override val name = "The Library of Ohara"

    override val baseUrl = "https://thelibraryofohara.com"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    private fun popularMangaSelector() = when (lang) {
        "en" ->
            "#categories-7 ul li.cat-item-589813936," + // Chapter Secrets
                "#categories-7 ul li.cat-item-607613583, " + // Chapter Secrets Specials
                "#categories-7 ul li.cat-item-43972770, " + // Charlotte Family
                "#categories-7 ul li.cat-item-9363667, " + // Complete Guides
                "#categories-7 ul li.cat-item-634609261, " + // Parody Chapter
                "#categories-7 ul li.cat-item-699200615, " + // Return to the Reverie
                "#categories-7 ul li.cat-item-139757, " + // SBS
                "#categories-7 ul li.cat-item-22695, " + // Timeline
                "#categories-7 ul li.cat-item-648324575"

        // Vivre Card Databook
        "id" -> "#categories-7 ul li.cat-item-702404482, #categories-7 ul li.cat-item-699200615"

        // Chapter Secrets Bahasa Indonesia, Return to the Reverie
        "fr" -> "#categories-7 ul li.cat-item-699200615"

        // Return to the Reverie
        "ar" -> "#categories-7 ul li.cat-item-699200615"

        // Return to the Reverie
        "it" -> "#categories-7 ul li.cat-item-699200615"

        // Return to the Reverie
        else -> "#categories-7 ul li.cat-item-693784776, #categories-7 ul li.cat-item-699200615" // Chapter Secrets (multilingual), Return to the Reverie
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            SManga.create().apply {
                title = element.select("a").text()
                setUrlWithoutDomain(element.select("a").attr("abs:href"))
            }
        }
        return MangasPage(mangas, false)
    }

    // Latest - not supported

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(searchMangaRequest(page, query, filters))
        .asObservableSuccess()
        .map { response ->
            searchMangaParse(response, query)
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    private fun searchMangaParse(response: Response, query: String): MangasPage = MangasPage(popularMangaParse(response).mangas.filter { it.title.contains(query, ignoreCase = true) }, false)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val title = document.select("h1.page-title").text().replace("Category: ", "")
        return SManga.create().apply {
            this.title = title
            thumbnail_url = chooseChapterThumbnail(document, title)
            description = ""
            status = SManga.ONGOING
        }
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
                chapterTitle.contains(siteLang) || (lang == "en" && !chapterTitle.contains(reverieLangRegex))
            }
        }
        // Chapter Secrets (multilingual)
        if (mangaTitle.contains("Chapter Secrets") && lang != "en") {
            imgElement = document.select("article").firstOrNull {
                val chapterTitle = it.select("h2.entry-title a").text()
                (lang == "id" && chapterTitle.contains("Indonesia")) || (lang == "es" && !chapterTitle.contains("Indonesia"))
            }
        }

        // Fallback
        imgElement = imgElement ?: document.select("article:first-of-type").firstOrNull()
        return imgElement?.select("img")?.attr("abs:src")
    }

    // Chapters

    private fun chapterNextPageSelector() = "div.nav-previous a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var document = response.asJsoup()

        while (true) {
            val pageChapters = document.select("article").map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.select("a.entry-thumbnail").attr("abs:href"))
                    name = element.select("h2.entry-title a").text()
                    date_upload = dateFormat.tryParse(
                        element.select("span.posted-on time").attr("datetime").replace("+00:00", "+0000"),
                    )
                }
            }
            if (pageChapters.isEmpty()) {
                break
            }

            allChapters += pageChapters

            val nextLink = document.select(chapterNextPageSelector())
            if (nextLink.isEmpty()) {
                break
            }

            val nextUrl = nextLink.attr("abs:href")
            document = client.newCall(GET(nextUrl, headers)).execute().asJsoup()
        }

        if (allChapters.isNotEmpty() && allChapters[0].name.contains("Reverie")) {
            return when (lang) {
                "fr" -> allChapters.filter { it.name.contains("French") }
                "ar" -> allChapters.filter { it.name.contains("Arabic") }
                "it" -> allChapters.filter { it.name.contains("Italian") }
                "id" -> allChapters.filter { it.name.contains("Indonesia") }
                "es" -> allChapters.filter { it.name.contains("Spanish") }
                else -> allChapters.filter {
                    !it.name.contains("French") &&
                        !it.name.contains("Arabic") &&
                        !it.name.contains("Italian") &&
                        !it.name.contains("Indonesia") &&
                        !it.name.contains("Spanish")
                }
            }
        }

        // Remove Indonesian posts if lang is spanish
        if (lang == "es") {
            return allChapters.filter { !it.name.contains("Indonesia") }
        }

        return allChapters
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.entry-content").select("a img, img.size-full").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("data-orig-file"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private val reverieLangRegex = Regex("""(French|Arabic|Italian|Indonesia|Spanish)""")
    }
}
