package eu.kanade.tachiyomi.extension.id.onepieceberwarna

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class OnePieceBerwarna : ParsedHttpSource() {

    override val name = "One Piece Berwarna"

    override val baseUrl = "https://onepieceberwarna.com"

    override val lang = "id"

    override val supportsLatest = false

    // one page contains all chapters
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector() = "section[data-id]:not(:first-child)"

    override fun popularMangaParse(response: Response): MangasPage {
        val mainElements = response.asJsoup()
        val list = mutableListOf<SManga>()
        var saga: String? = null
        var volume: String? = null

        val sections = mainElements.select(popularMangaSelector())

        run loop@{
            sections.forEach { element ->
                val dataID = element.attr("data-id")
                if (dataID == "49afe94") {
                    // break on non-manga section
                    return@loop
                }

                element.select(".elementor-col-100:not(:has(div[data-element_type=icon.default])) h1")
                    .first()?.let {
                        saga = it.text()
                    }

                element.select(".elementor-col-50 h1").first()?.let {
                    volume = it.text()
                }

                element.select(".elementor-widget-container > img[data-src]").first()?.let {
                    list.add(
                        SManga.create().apply {
                            title = "$saga $volume"
                            thumbnail_url = it.attr("data-src")
                            url = dataID // just for identifier
                        },
                    )
                }
            }
        }
        return MangasPage(list, false)
    }

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl, headers)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        author = "Eiichiro Oda"
        description = "Bercerita tentang seorang laki-laki bernama Monkey D. Luffy, yang menentang arti dari gelar bajak laut. Daripada kesan nama besar, kejahatan, kekerasan, dia lebih terlihat seperti bajak laut rendahan yang suka bersenang-senang, alasan Luffy menjadi bajak laut adalah tekadnya untuk berpetualang di lautan yang menyenangkan dan bertemu orang-orang baru dan menarik, serta bersama-sama mencari One Piece."
        genre = "Action, Adventure, Comedy, Fantasy, Martial Arts, Mystery, Shounen, Supernatural"
    }

    override fun chapterListSelector(): String = "section[data-id=%s] .elementor-text-editor strong > a"

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/#${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val dataId = response.request.url.fragment!!
        val selector = chapterListSelector().format(dataId)

        return document.select(selector)
            .map(::chapterFromElement)
            .reversed()
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".entry-content img[data-src]:not(a img)")
            .mapIndexed { index, img ->
                Page(index, "", img.attr("data-src"))
            }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    // website doesn't have search
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Not used")

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")
}
