package eu.kanade.tachiyomi.extension.all.kanjiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Kanjiku(
    override val lang: String,
    subDomain: String,
) : ParsedHttpSource() {

    override val name = "Kanjiku"
    override val baseUrl = "https://${subDomain}kanjiku.net"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/mangas", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest", headers)

    override fun popularMangaSelector(): String = ".manga_box"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst(".manga_title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(".manga_overview_box_headline a").map { element ->
            SManga.create().apply {
                var url = element.absUrl("href").toHttpUrl()
                if (url.pathSegments.last() == "") {
                    // remove empty path segment
                    url = url.newBuilder().removePathSegment(url.pathSegments.lastIndex).build()
                }
                setUrlWithoutDomain(url.toString())
                title = element.text()
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return Observable.just(
            MangasPage(
                client.newCall(popularMangaRequest(page)).execute().asJsoup()
                    .select(popularMangaSelector()).map { popularMangaFromElement(it) }
                    .filter { query.lowercase() in it.title.lowercase() },
                false,
            ),
        )
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".manga_page_title")!!.text()
        description = document.selectFirst(".manga_description")?.text()
        thumbnail_url = document.selectFirst(".manga_page_picture")?.absUrl("src")
        status = when (
            document.selectFirst(".tags .tag_container_special .tag")?.absUrl("href")
                ?.toHttpUrl()?.pathSegments?.last()
        ) {
            "47" -> SManga.ONGOING
            "48" -> SManga.COMPLETED
            "49" -> SManga.ON_HIATUS
            "50" -> SManga.CANCELLED
            "51" -> SManga.LICENSED
            else -> SManga.UNKNOWN // using tag ids so that it works in all languages
        }
        genre = document.select(".tags .tag_container .tag").joinToString { it.text() }
    }

    override fun chapterListSelector(): String = ".manga_chapter a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(
            element.absUrl("href").toHttpUrl().run {
                newBuilder().setPathSegment(pathSegments.lastIndex, "0").build()
            }.toString(),
        )
        name = element.text()
    }

    override fun pageListParse(document: Document): List<Page> =
        document.select(".container img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }

    override fun latestUpdatesFromElement(element: Element): SManga =
        throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga =
        throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException()

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()
    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector(): String? = null
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun imageUrlParse(document: Document): String = ""
}
