package eu.kanade.tachiyomi.extension.en.egscomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class ElGoonishShive : HttpSource() {
    override val name = "El Goonish Shive"
    override val baseUrl = "https://www.egscomics.com"
    override val lang = "en"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = listOf(
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
            SManga.create().apply {
                title = "$name Sketchbook"
                artist = "Dan Shive"
                author = artist
                status = SManga.ONGOING
                url = "/sketchbook/archive"
                description = "El Goonish Shive is a comic about a group of teenagers who face " +
                    "both real life and bizarre, supernatural situations. \n\n" +
                    "It is a comedy mixed with drama and is recommended for audiences thirteen " +
                    "and older. \n\n" +
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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("select[name=comic] option[value~=^(comic|egsnp|sketchbook)]").map { element ->
            SChapter.create().apply {
                chapter_number = element.previousElementSiblings().size.toFloat()
                setUrlWithoutDomain("/" + element.attr("value"))
                name = element.text().split(" - ", limit = 2).last()
                date_upload = dateFormat.tryParse(element.text().split(" - ", limit = 2).first())
            }
        }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#cc-comic").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
