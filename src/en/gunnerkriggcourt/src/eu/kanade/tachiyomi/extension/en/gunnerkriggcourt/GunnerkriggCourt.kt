package eu.kanade.tachiyomi.extension.en.gunnerkriggcourt

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class GunnerkriggCourt : HttpSource() {
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
            description = """
                Gunnerkrigg Court is a Science Fantasy webcomic by Tom Siddell about a strange young girl attending an equally strange school. The intricate story is deeply rooted in world mythology, but has a strong focus on science (chemistry and robotics, most prominently) as well.

                Antimony Carver begins classes at the eponymous U.K. Boarding School, and soon notices that strange events are happening: a shadow creature follows her around; a robot calls her "Mummy"; a Rogat Orjak smashes in the dormitory roof; odd birds, ticking like clockwork, stand guard in out-of-the-way places.

                Stranger still, in the middle of all this, Annie remains calm and polite to a fault.
            """.trimIndent()
            thumbnail_url = "https://i.imgur.com/g2ukAIKh.jpg"
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div.chapters option[value~=\\d*]").map { element ->
            SChapter.create().apply {
                val chapterNumStr = element.attr("value")
                chapter_number = chapterNumStr.toFloatOrNull() ?: -1f
                setUrlWithoutDomain("/?p=$chapterNumStr")

                val title = element.parent()?.previousElementSibling()?.text() ?: "Chapter"
                name = "$title (${if (chapter_number >= 0f) chapter_number.toInt() else chapterNumStr})"
            }
        }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".comic_image").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
}
