package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NiaddEn : ParsedHttpSource() {

    override val name = "Niadd (EN)"
    override val lang = "en"
    override val supportsLatest = true

    // domínio principal
    override val baseUrl = "https://www.niadd.com"
    // domínio alternativo (alguns capítulos são redirecionados pra cá)
    private val altBaseUrl = "https://www.nineanime.com"

    // ==============================
    // Popular Manga
    // ==============================
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/$page", headers)
    }

    override fun popularMangaSelector() = "div.comic-box"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        manga.title = element.selectFirst("p.comic-title")?.text().orEmpty()
        manga.thumbnail_url = element.selectFirst("img")?.attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "a:contains(Next)"

    // ==============================
    // Latest Updates
    // ==============================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ==============================
    // Search
    // ==============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/?name=$query&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ==============================
    // Manga Details
    // ==============================
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1.comic-title")?.text().orEmpty()
        manga.author = document.select("p:contains(Author)").text().removePrefix("Author(s): ")
        manga.artist = document.select("p:contains(Artist)").text().removePrefix("Artist(s): ")
        manga.genre = document.select("p:contains(Genres)").text().removePrefix("Genres: ")
        manga.description = document.select("div.comic-intro").text()
        manga.thumbnail_url = document.selectFirst("div.comic-img img")?.attr("src")
        return manga
    }

    // ==============================
    // Chapter List
    // ==============================
    override fun chapterListSelector() = "ul.chapter-list li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        chapter.name = element.selectFirst("a")!!.text()
        chapter.date_upload = 0L // Niadd não mostra datas
        return chapter
    }

    // ==============================
    // Page List
    // ==============================
    override fun pageListRequest(chapter: SChapter): Request {
        val url = when {
            chapter.url.startsWith("http") -> chapter.url
            else -> baseUrl + chapter.url
        }

        // Se já caiu no domínio alternativo
        val finalUrl = when {
            url.contains("nineanime.com") -> url.replace("niadd.com", "nineanime.com")
            else -> url
        }

        return GET(finalUrl, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        // Niadd usa div.pic_box
        val niaddImages = document.select("div.pic_box img")
        if (niaddImages.isNotEmpty()) {
            niaddImages.forEachIndexed { i, img ->
                pages.add(Page(i, "", img.attr("src")))
            }
            return pages
        }

        // NineAnime usa img[data-src] (fallback)
        val nineImages = document.select("div.reader img[data-src]")
        if (nineImages.isNotEmpty()) {
            nineImages.forEachIndexed { i, img ->
                pages.add(Page(i, "", img.attr("data-src")))
            }
            return pages
        }

        return pages
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("Not used.")
}
