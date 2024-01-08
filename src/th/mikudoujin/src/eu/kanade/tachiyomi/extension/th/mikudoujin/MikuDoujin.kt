package eu.kanade.tachiyomi.extension.th.mikudoujin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MikuDoujin : ParsedHttpSource() {

    override val baseUrl: String = "https://miku-doujin.com"

    override val lang: String = "th"
    override val name: String = "MikuDoujin"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.col-6.inz-col"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("div.inz-title").text()
            manga.thumbnail_url = it.select("img").attr("abs:src")
            manga.initialized = false
        }

        return manga
    }

    override fun popularMangaNextPageSelector() = "button.btn-secondary"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    private fun genre(name: String): String {
        return if (name != "สาวใหญ่/แม่บ้าน") {
            URLEncoder.encode(name, "UTF-8")
        } else {
            name
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw Exception("Unused")

    override fun searchMangaSelector(): String = throw Exception("Unused")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Unused")

    override fun searchMangaNextPageSelector(): String = throw Exception("Unused")

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val searchMethod = query.startsWith("http")
        return client.newCall(
            GET(
                if (searchMethod) {
                    query
                } else {
                    "$baseUrl/genre/${genre(query)}/?page=$page"
                },
            ),
        )
            .asObservableSuccess()
            .map {
                val document = it.asJsoup()
                val mangas: List<SManga> = if (searchMethod) {
                    listOf(
                        SManga.create().apply {
                            url = query.substringAfter(baseUrl)
                            title = document.title()
                            thumbnail_url =
                                document.select("div.sr-card-body div.col-md-4 img").attr("abs:src")
                            initialized = false
                        },
                    )
                } else {
                    document.select(popularMangaSelector()).map { element ->
                        popularMangaFromElement(element)
                    }
                }

                MangasPage(mangas, !searchMethod)
            }
    }

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.sr-card-body").first()!!

        return SManga.create().apply {
            title = document.title()
            author = infoElement.select("div.col-md-8 p a.badge-secondary")[2].ownText()
            artist = author
            status = SManga.UNKNOWN
            genre = infoElement.select("div.sr-card-body div.col-md-8 div.tags a")
                .joinToString { it.text() }
            description = infoElement.select("div.col-md-8").first()!!.ownText()
            thumbnail_url = infoElement.select("div.col-md-4 img").first()!!.attr("abs:src")
            initialized = true
        }
    }

    // Chapters

    override fun chapterListSelector() = "table.table-episode tr"

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Unused")

    private fun chapterFromElementWithIndex(element: Element, idx: Int, manga: SManga): SChapter {
        val chapter = SChapter.create()
        element.select("td a").let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.text()
            if (chapter.name.isEmpty()) {
                chapter.chapter_number = 0.0f
            } else {
                val lastWord = chapter.name.split(" ").last()
                try {
                    chapter.chapter_number = lastWord.toFloat()
                } catch (ex: NumberFormatException) {
                    if (lastWord == "จบ") {
                        manga.status = SManga.COMPLETED
                    }
                    chapter.chapter_number = (idx + 1).toFloat()
                }
            }
        }

        return chapter
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(GET("$baseUrl/${manga.url}"))
            .asObservableSuccess()
            .map {
                val chList: List<SChapter>
                val mangaDocument = it.asJsoup()

                if (mangaDocument.select(chapterListSelector()).isEmpty()) {
                    manga.status = SManga.COMPLETED
                    val createdChapter = SChapter.create().apply {
                        url = manga.url
                        name = "Chapter 1"
                        chapter_number = 1.0f
                    }
                    chList = listOf(createdChapter)
                } else {
                    chList =
                        mangaDocument.select(chapterListSelector()).mapIndexed { idx, Chapter ->
                            chapterFromElementWithIndex(Chapter, idx, manga)
                        }
                }
                chList
            }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#v-pills-tabContent img.lazy").mapIndexed { i, img ->
            if (img.hasAttr("data-src")) {
                Page(i, "", img.attr("abs:data-src"))
            } else {
                Page(i, "", img.attr("abs:src"))
            }
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
