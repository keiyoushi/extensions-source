package eu.kanade.tachiyomi.extension.all.rokuhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class RokuHentai : HttpSource() {

    override val baseUrl = "https://rokuhentai.com"
    override val lang = "all"
    override val name = "Roku Hentai"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder().add("Referer", baseUrl)

    // Customize

    private var offset: String? = null
    private val SManga.id get() = url.substringAfterLast('/')

    companion object {
        val DATE_FORMAT = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US)
        val IMG_REGEX = Regex("background-image: url\\(\"(.+?)\"\\);")
    }

    private fun parseManga(e: Element) = SManga.create().apply {
        val info = e.selectFirst(".mdc-typography--caption:last-child")!!.text().split(" images ")
        setUrlWithoutDomain(e.absUrl("href").substringBeforeLast('/') + "#${info[0]},${DATE_FORMAT.tryParse(info[1])}")
        title = e.selectFirst(".site-manga-card__title--primary")!!.text()
        thumbnail_url = IMG_REGEX.matchEntire(e.selectFirst(".mdc-card__media")!!.attr("style"))?.groups[1]?.value
    }

    // Popular Page

    override fun popularMangaRequest(page: Int): Request = if (page == 1) GET(baseUrl) else GET("$baseUrl/_search?p=$offset")

    override fun popularMangaParse(response: Response): MangasPage {
        val type = response.header("Content-Type")!!
        val mangas = if (type.contains("text/html")) {
            response.asJsoup().select(".mdc-card > .site-popunder-ad-slot").map(::parseManga)
        } else {
            response.parseAs<SearchResult>().mangaCards.map {
                parseManga(Jsoup.parse(it, baseUrl).selectFirst(".site-popunder-ad-slot")!!)
            }
        }
        offset = mangas.last().id
        return MangasPage(mangas, mangas.size == 24)
    }

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // Search Page

    override fun getFilterList() = FilterList(
        Filter.Header("Search How-To:"),
        Filter.Header("• Search for a phrase in manga titles: this is a title."),
        Filter.Header("• Search for manga titles containing all phrases (double quotes are required to separate phrases): \"this foo\" \"that bar\"."),
        Filter.Header("• Exclude manga titles containing a phrase with \"-\" (double quotes are required): -\"this is excluded\"."),
        Filter.Header("• Filter mangas by tag: group:foo."),
        Filter.Header("• You must double-quote a tag if it contains spaces: parody:\"foo bar\"."),
        Filter.Header("• Filter mangas by date: after:2000-01-01 before:2000-02."),
        Filter.Header("• Negate a filter with \"-\": -language:foo."),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = if (page == 1) GET("$baseUrl?q=$query") else GET("$baseUrl/_search?p=$offset&q=$query")

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Manga Detail Page

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        val img = doc.selectFirst(".site-manga-info .mdc-card__media")
        val titles = doc.select(".site-manga-info__info h6")
        return SManga.create().apply {
            thumbnail_url = IMG_REGEX.matchEntire(img!!.attr("style"))?.groups[1]?.value
            description = titles.getOrNull(1)?.text()
            genre = doc.select(".mdc-chip").joinToString {
                val text = it.text().split(": ")
                if (text[1].contains(' ')) "${text[0]}: \"${text[1]}\"" else it.text()
            }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // Catalog Page

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                setUrlWithoutDomain("${manga.url.substringBefore('#')}/0")
                name = manga.title
                date_upload = manga.url.substringAfter(',').toLong()
                chapter_number = 0F
                scanlator = "${manga.url.substringAfter('#').substringBefore(',')}P"
            },
        ),
    )

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    // Manga View Page

    override fun pageListParse(response: Response) = response.asJsoup().select(".site-reader > img").mapIndexed { i, e ->
        Page(i, imageUrl = e.attr("data-src"))
    }

    // Image

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
