package eu.kanade.tachiyomi.extension.en.egscomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class ElGoonishShive : ParsedHttpSource() {
    override val name = "El Goonish Shive"
    override val baseUrl = "https://www.egscomics.com"
    override val lang = "en"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = arrayListOf<SManga>()
        manga.add(
            SManga.create().apply {
                title = name
                artist = "Dan Shive"
                author = artist
                status = SManga.ONGOING
                url = "/comic/archive"
                description = "El Goonish Shive is a comic about a group of teenagers who face " +
                    "both real life and bizarre, supernatural situations. \n\n" +
                    "It is a comedy mixed with drama and is recommended for audiences thirteen " +
                    "and older."
                thumbnail_url =
                    "https://static.tumblr.com/8cee5e83d26a8a96ad5e51b67f2e340e/j8ipbno/fXFoj0zh9/tumblr_static_1f2fhwjyya74gsgs888g8k880.png"
                initialized = true
            },
        )

        manga.add(
            SManga.create().apply {
                title = "$name: NewsPaper"
                artist = "Dan Shive"
                author = artist
                status = SManga.ONGOING
                url = "/egsnp/archive"
                description = "El Goonish Shive is a comic about a group of teenagers who face " +
                    "both real life and bizarre, supernatural situations. \n\n" +
                    "It is a comedy mixed with drama and is recommended for audiences thirteen " +
                    "and older. \n\n" +
                    "EGS:NP is a subsection with short stories that generally aren't canon " +
                    "unless stated"
                thumbnail_url =
                    "https://static.tumblr.com/8cee5e83d26a8a96ad5e51b67f2e340e/j8ipbno/fXFoj0zh9/tumblr_static_1f2fhwjyya74gsgs888g8k880.png"
                initialized = true
            },
        )

        manga.add(
            SManga.create().apply {
                title = "$name Sketchbook"
                artist = "Dan Shive"
                author = artist
                status = SManga.ONGOING
                url = "/sketchbook/archive"
                description = "El Goonish Shive is a comic about a group of teenagers who face " +
                    "both real life and bizarre, supernatural situations. \n\n" +
                    "It is a comedy mixed with drama and is recommended for audiences thirteen " +
                    "and older. \n\n" + "" +
                    "The Sketchbook section is full of one-shot gags, sketches, comics that " +
                    "don't fit elsewhere."
                thumbnail_url =
                    "https://static.tumblr.com/8cee5e83d26a8a96ad5e51b67f2e340e/j8ipbno/fXFoj0zh9/tumblr_static_1f2fhwjyya74gsgs888g8k880.png"
                initialized = true
            },
        )

        return Observable.just(MangasPage(manga, false))
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun chapterListSelector() =
        """select[name=comic] option[value~=^(comic|egsnp|sketchbook)]"""

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            chapter_number = element.previousElementSiblings().size.toFloat()
            setUrlWithoutDomain("/" + element.attr("value"))
            name = element.text().split(" - ", limit = 2).last()
            date_upload = dateFormat.tryParse(element.text().split(" - ", limit = 2).first())
        }
    }

    override fun pageListParse(document: Document): List<Page> =
        document.select("#cc-comic").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }

    // <editor-fold desc="Not Used">
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
    override fun popularMangaSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaSelector(): String = throw UnsupportedOperationException()
    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector(): String = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector(): String = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    // </editor-fold>
}
