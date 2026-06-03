package eu.kanade.tachiyomi.extension.en.imanevilgod

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.Jsoup

class IAmAnEvilGod : HttpSource() {

    override val name = "I'm An Evil God"
    override val baseUrl = "https://imanevilgod.com"
    override val lang = "en"
    override val supportsLatest = false

    // --- Catalogue (single entry) ---

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val manga = SManga.create().apply {
            title = "I'm An Evil God"
            url = "/"
            status = SManga.UNKNOWN
        }
        return MangasPage(listOf(manga), false)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = popularMangaRequest(page)
    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // --- Manga details ---

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl, headers)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        title = "I'm An Evil God"
        url = "/"
        status = SManga.UNKNOWN
        description = "Across the realms, the manliest and most handsome evil god in history! " +
            "Xie Yan crosses over and falls into the vixen's lair..."
    }

    // --- Chapter list ---

    override fun chapterListRequest(manga: SManga) = GET(baseUrl, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        // Chapters are <a> tags inside the paragraph with class "has-medium-font-size"
        return doc.select("p.has-medium-font-size a[href*=imanevilgod.com]")
            .mapIndexed { index, el ->
                SChapter.create().apply {
                    name = el.text()
                    url = el.attr("href")
                    chapter_number = index.toFloat()
                }
            }
    }

    // --- Page list ---

    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        // Chapter pages are <img> tags inside the post content
        return doc.select("div.entry-content img")
            .mapIndexed { index, el ->
                Page(index, "", el.attr("src").ifEmpty { el.attr("data-src") })
            }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
