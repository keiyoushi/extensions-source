package eu.kanade.tachiyomi.extension.en.gunnerkriggcourt

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class GunnerkriggCourt : ParsedHttpSource() {
    override val name = "Gunnerkrigg Court"
    override val baseUrl = "https://www.gunnerkrigg.com"
    override val lang = "en"
    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = name
            artist = "Tom Siddell"
            author = artist
            status = SManga.ONGOING
            url = "/archives/"
            description = "Gunnerkrigg Court is a Science Fantasy webcomic by Tom Siddell about a" +
                " strange young girl attending an equally strange school. The intricate story is " +
                "deeply rooted in world mythology, but has a strong focus on science (chemistry " +
                "and robotics, most prominently) as well.\n\n" +
                "Antimony Carver begins classes at the eponymous U.K. Boarding School, and soon " +
                "notices that strange events are happening: a shadow creature follows her around;" +
                " a robot calls her \"Mummy\"; a Rogat Orjak smashes in the dormitory roof; odd " +
                "birds, ticking like clockwork, stand guard in out-of-the-way places.\n\n" +
                "Stranger still, in the middle of all this, Annie remains calm and polite to a fault."
            thumbnail_url = "https://i.imgur.com/g2ukAIKh.jpgss"
        }

        return Observable.just(MangasPage(arrayListOf(manga).reversed(), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun chapterListSelector() =
        """div.chapters option[value~=\d*]"""

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            chapter_number = element.attr("value").toFloat()
            setUrlWithoutDomain("/?p=" + element.attr("value"))
            name = element.parent()!!.previousElementSibling()!!.text() + " (" + chapter_number.toInt() + ")"
            // date_upload // Find by using hovertext above "Tom" on actual comic page
        }
    }

    override fun pageListParse(document: Document): List<Page> =
        document.select(".comic_image").mapIndexed { i, element -> Page(i, "", baseUrl + element.attr("src")) }

    // <editor-fold desc="Not Used">
    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")
    // </editor-fold>
}
