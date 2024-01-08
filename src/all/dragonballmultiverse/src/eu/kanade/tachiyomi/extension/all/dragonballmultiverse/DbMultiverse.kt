package eu.kanade.tachiyomi.extension.all.dragonballmultiverse

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

abstract class DbMultiverse(override val lang: String, private val internalLang: String) : ParsedHttpSource() {

    override val name =
        if (internalLang.endsWith("_PA")) {
            "Dragon Ball Multiverse Parody"
        } else {
            "Dragon Ball Multiverse"
        }
    override val baseUrl = "https://www.dragonball-multiverse.com"
    override val supportsLatest = false

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val href = element.attr("href")
        chapter.setUrlWithoutDomain("/$internalLang/$href")
        chapter.name = "Page " + element.text()

        return chapter
    }

    override fun chapterListSelector(): String = ".cadrelect.chapter p a[href*=-]"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#h_read img").mapIndexed { index, element ->
            Page(index, "", element.attr("abs:src"))
        }
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        // site hosts three titles that can be read by the app
        return listOf("page", "strip", "namekseijin")
            .map { createManga(it) }
            .let { Observable.just(MangasPage(it, hasNextPage = false)) }
    }

    private fun createManga(type: String) = SManga.create().apply {
        title = when (type) {
            "comic" -> "DB Multiverse"
            "namekseijin" -> "Namekseijin Densetsu"
            "strip" -> "Minicomic"
            else -> name
        }
        status = SManga.ONGOING
        url = "/$internalLang/chapters.html?comic=$type"
        description = "Dragon Ball Multiverse (DBM) is a free online comic, made by a whole team of fans. It's our personal sequel to DBZ."
        thumbnail_url = "$baseUrl/imgs/read/$type.jpg"
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return manga.apply {
            initialized = true
        }.let { Observable.just(it) }
    }

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not Used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaSelector(): String = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
}
