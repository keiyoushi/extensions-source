package eu.kanade.tachiyomi.extension.en.imanevilgod

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable

@Source
abstract class IAmAnEvilGod : HttpSource() {

    override val supportsLatest = false

    // --- Catalogue (single entry) ---

    private fun createManga() = SManga.create().apply {
        title = "I'm An Evil God"
        url = "/"
        status = SManga.UNKNOWN
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(createManga()), false))

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(listOf(createManga()), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    // --- Manga details ---

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = "I'm An Evil God"
            url = "/"
            status = SManga.UNKNOWN
            description = "Across the realms, the manliest and most handsome evil god in history! " +
                "Xie Yan crosses over and falls into the vixen's lair..."
            thumbnail_url = doc.selectFirst("meta[property=og:image]")?.attr("content")
        }
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
                    setUrlWithoutDomain(el.absUrl("href"))
                    chapter_number = index.toFloat()
                }
            }
    }

    // --- Page list ---

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        // Chapter pages are <img> tags inside the post content
        return doc.select("div.entry-content img")
            .mapIndexed { index, el ->
                Page(index, "", el.absUrl("src").ifEmpty { el.absUrl("data-src") })
            }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
