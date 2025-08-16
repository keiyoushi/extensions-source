package eu.kanade.tachiyomi.extension.fr.invinciblecomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class InvincibleComics : HttpSource() {
    override val name = "Invincible Comicsvf"

    override val baseUrl = "https://invinciblecomicsvf.fr"

    override val lang = "fr"

    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(".top-comics-grid .comic-card, .top-bd .comic-card").map { element ->
            parseMangaFromElement(element)
        }

        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(".nouveaux-ajouts .comic-card").map { element ->
            parseMangaFromElement(element)
        }

        return MangasPage(mangas, false)
    }

    private fun parseMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        thumbnail_url = element.selectFirst("img")?.parseSrcset()
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/page/$page/?s=$query&ct_post_type=comic:bande_dessine".toHttpUrl()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".entry-card").map { element ->
            SManga.create().apply {
                title = element.selectFirst("a")?.attr("aria-label")!!
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                thumbnail_url = element.selectFirst("img")?.attr("data-src")
            }
        }

        return MangasPage(mangas, document.selectFirst(".next") != null)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create()
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return emptyList()
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return emptyList()
    }

    // Page
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

/*
    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }
*/
    // Filters

    // Utils
    private fun Element.parseSrcset(): String {
        if (!this.hasAttr("data-srcset")) {
            return this.attr("data-src")
        }
        val parts = this.attr("data-srcset").split(",").map { it.trim() }
        return parts.last { it.contains(" ") }.substringBefore(" ")
    }
}
