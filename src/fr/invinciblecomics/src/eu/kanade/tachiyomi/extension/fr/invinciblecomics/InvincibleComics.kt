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
    override val name = "Invincible ComicsVF"

    override val baseUrl = "https://invinciblecomicsvf.fr"

    override val lang = "fr"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val req = chain.request()
        val res = chain.proceed(req)

        if (req.url.fragment != "page" || res.code != 404) return@addInterceptor res

        res.close()
        val newRequest = req.newBuilder()
            .url(chain.request().url.toString().replace(".jpg", ".png"))
            .build()

        chain.proceed(newRequest)
    }.build()

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
        return SManga.create().apply {
            thumbnail_url = document.selectFirst("#main img")?.parseSrcset()
            description = document.selectFirst("strong:contains(Résumé)")?.parent()?.ownText()
            author = document.selectFirst("strong:contains(Auteur)")?.parent()?.ownText()
            genre = document.selectFirst("strong:contains(Genres)")?.parent()?.ownText()
            status = when (document.selectFirst("strong:contains(Status)")?.parent()?.ownText()?.trim()) {
                "En cours" -> SManga.ONGOING
                "Términé" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a.tome-link").map { element ->
            SChapter.create().apply {
                name = element.text()
                setUrlWithoutDomain(element.attr("href"))
                date_upload = 0L
                chapter_number = element.attr("data-tome").toFloatOrNull() ?: 0f
            }
        }.reversed()
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val script = document.selectFirst("script:containsData(imageBase)")?.data()
            ?: error("No script with imageBase found")
        val imageBaseUrl = Regex("""imageBase = "(.*)";""").find(script)?.groupValues?.get(1)
            ?: error("Failed to extract imageBase URL from script")
        val totalPages = Regex("""const totalPages = (\d+);""").find(script)?.groupValues?.get(1)
            ?: error("Failed to extract total pages from script")

        return (1..totalPages.toInt()).map { pageNumber ->
            Page(pageNumber, imageUrl = "$imageBaseUrl${"%03d".format(pageNumber)}.jpg#page")
        }
    }

    // Page
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Utils
    private fun Element.parseSrcset(): String {
        if (!this.hasAttr("data-srcset")) {
            return this.attr("data-src")
        }
        val parts = this.attr("data-srcset").split(",").map { it.trim() }
        return parts.last { it.contains(" ") }.substringBefore(" ")
    }
}
