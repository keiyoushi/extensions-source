package eu.kanade.tachiyomi.extension.all.yaoimangaonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

class YaoiMangaOnline : HttpSource() {
    override val lang = "all"

    override val name = "Yaoi Manga Online"

    override val baseUrl = "https://yaoimangaonline.com"

    override val supportsLatest = false

    // =================== Popular ===================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".post:not(.category-gay-movies):not(.category-yaoi-anime) > div > a")
            .map { element ->
                SManga.create().apply {
                    title = element.attr("title")
                    setUrlWithoutDomain(element.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")?.attr("src")
                }
            }
        val hasNextPage = document.selectFirst(".herald-pagination > .next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =================== Latest ===================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =================== Search ===================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = baseUrl.toHttpUrl().newBuilder().run {
        filters.forEach {
            when (it) {
                is CategoryFilter -> if (it.state != 0) {
                    addQueryParameter("cat", it.toString())
                }
                is TagFilter -> if (it.state != 0) {
                    addEncodedPathSegments("tag/$it")
                }
                else -> {}
            }
        }
        addEncodedPathSegments("page/$page")
        addQueryParameter("s", query)
        GET(toString(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // =================== Details ===================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select("h1.entry-title").text()
            .substringBeforeLast("by").trim()
        thumbnail_url = document.selectFirst(".herald-post-thumbnail img")?.attr("src")
        description = document
            .select(".entry-content > p:not(:has(img)):not(:contains(You need to login))")
            .joinToString("\n\n") { it.wholeText() }
        genre = document.select(".meta-tags > a").joinToString { it.text() }
        author = document.select(".entry-content > p:contains(Mangaka:)").text()
            .substringAfter("Mangaka:")
            .substringBefore("Language:")
            .trim()
    }

    // =================== Chapters ===================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(".mpp-toc a").map { element ->
            SChapter.create().apply {
                name = element.ownText()
                setUrlWithoutDomain(element.absUrl("href").ifEmpty { element.baseUri() })
            }
        }
        return chapters.ifEmpty {
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = response.request.url.encodedPath
                },
            )
        }.reversed()
    }

    // =================== Pages ===================

    override fun pageListParse(response: Response) = response.asJsoup()
        .select(".entry-content img")
        .mapIndexed { idx, img -> Page(idx, imageUrl = img.attr("src")) }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(CategoryFilter(), TagFilter())
}
