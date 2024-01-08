package eu.kanade.tachiyomi.extension.en.keenspot

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import kotlin.math.min

class TwoKinds : HttpSource() {

    override val name = "Keenspot TwoKinds"

    override val baseUrl = "https://twokinds.keenspot.com"

    override val lang = "en"

    override val supportsLatest: Boolean = false

    override val id: Long = 3133607736276627986

    // the one and only manga entry
    fun mangaSinglePages(): SManga {
        return SManga.create().apply {
            title = "TwoKinds (1 page per chapter)"
            thumbnail_url = "https://dummyimage.com/768x994/000/ffffff.jpg&text=$title"
            artist = "Tom Fischbach"
            author = "Tom Fischbach"
            status = SManga.UNKNOWN
            url = "1"
        }
    }

    fun manga20Pages(): SManga {
        return SManga.create().apply {
            title = "TwoKinds (20 pages per chapter)"
            thumbnail_url = "https://dummyimage.com/768x994/000/ffffff.jpg&text=$title"
            artist = "Tom Fischbach"
            author = "Tom Fischbach"
            status = SManga.UNKNOWN
            url = "20"
        }
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(mangaSinglePages(), manga20Pages()), false))
    }

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    // latest Updates not used

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    // the manga is one and only, but still write the data again to avoid bugs in backup restore
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        if (manga.url == "1") {
            return Observable.just(mangaSinglePages())
        } else {
            return Observable.just(manga20Pages())
        }
    }

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    // chapter list

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/archive/", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    data class TwoKindsPage(val url: String, val name: String)

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()

        val pages = document.select(".chapter-links")
            .flatMap { season -> season.select("> a") }
            .map { a ->
                // /comic/1185halloween/ -> 1185halloween
                val urlPart = a.attr("href").split("/")[2]
                val name = a.selectFirst("span")!!.text()

                TwoKindsPage(urlPart, name)
            }

        // 1 page per chapter
        if (manga.url == "1") {
            return pages.map { page ->
                SChapter.create().apply {
                    url = "1-${page.url}"
                    name = "Page ${page.name}"
                }
            }.reversed()
        }

        // 20 pages per chapter
        val chapters = mutableListOf<SChapter>()
        for (i in pages.indices step 20) {
            chapters.add(
                SChapter.create().apply {
                    url = "20-${pages[i].url}"
                    name = "Pages ${pages[i].name}-${pages[min(pages.size, i + 20) - 1].name}"
                },
            )
        }
        return chapters.reversed()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (chapter.url.startsWith("1")) {
            return Observable.just(
                listOf(
                    Page(0, baseUrl + "/comic/${chapter.url.substringAfter("-")}/"),
                ),
            )
        } else {
            val firstPage = chapter.url.substringAfter("-")
            val document = client.newCall(chapterListRequest(SManga.create())).execute().asJsoup()

            val pages = document.select(".chapter-links")
                .flatMap { season -> season.select("> a") }
                .map { a ->
                    // /comic/1185halloween/ -> 1185halloween
                    val urlPart = a.attr("href").split("/")[2]
                    val name = a.selectFirst("span")!!.text()

                    TwoKindsPage(urlPart, name)
                }

            val firstPageIdx = pages.indexOfFirst { it.url == firstPage }
            val lastPageIdx = min(pages.size, firstPageIdx + 20)

            return Observable.just(
                pages
                    .subList(firstPageIdx, lastPageIdx)
                    .mapIndexed { idx, page ->
                        Page(idx, baseUrl + "/comic/${page.url}/")
                    },
            )
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()

        return document.select("#content article img").first()!!.attr("src")
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Search functionality is not available.")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")
}
