package eu.kanade.tachiyomi.extension.fr.yaoiscan

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class YaoiScan : MangaReader() {

    override val name = "YaoiScan"

    override val lang = "fr"

    override val baseUrl = "https://yaoiscan.fr/"

    override val client = network.cloudflareClient.newBuilder()
        // .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/catalogue/?type=&status=&order=popular&page=$page", headers)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/catalogue/?type=&status=&order=update&page=$page", headers)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("s", query)
            } else {
                addPathSegment("catalogue")
                addPathSegment("")

                filters.ifEmpty(::getFilterList).forEach { filter ->
                    when (filter) {
                        is TypeFilter -> {
                            addQueryParameter(filter.param, filter.selection)
                        }
                        is StatusFilter -> {
                            addQueryParameter(filter.param, filter.selection)
                        }

                        is SortFilter -> {
                            addQueryParameter(filter.param, filter.selection)
                        }
                        is GenresFilter -> {
                            filter.state.forEach {
                                if (it.state) {
                                    addQueryParameter(filter.param, it.id)
                                }
                            }
                        }
                        else -> { }
                    }
                }
            }

            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".listupd .bsx a"

    override fun searchMangaFromElement(element: Element) =
        SManga.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            title = element.attr("title")
            element.selectFirst(Evaluator.Tag("img"))!!.let {
                thumbnail_url = it.imgAttr()
            }
        }

    override fun searchMangaNextPageSelector() = ".hpage a"

    // =============================== Filters ==============================

    override fun getFilterList() =
        FilterList(
            Note,
            Filter.Separator(),
            TypeFilter(),
            StatusFilter(),
            SortFilter(),
            GenresFilter(),
        )

    private fun statusMangaSelector() = ".status-value"

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val root = document.selectFirst(Evaluator.Class("seriestucon"))!!
        val mangaTitle = root.selectFirst(Evaluator.Class("mtitle"))!!.ownText()
        title = mangaTitle
        description = buildString {
            root.selectFirst(".entry-content.entry-content-single p")?.ownText()?.let { append(it) }

            // No alternative title :sob:
            // append("\n\n")
            // root.selectFirst(".manga-name-or")?.ownText()?.let {
            //     if (it.isNotEmpty() && it != mangaTitle) {
            //         append("Alternative Title: ")
            //         append(it)
            //     }
            // }
        }.trim()
        thumbnail_url = root.selectFirst(Evaluator.Tag("img"))!!.imgAttr()
        genre = root.selectFirst(Evaluator.Class("seriestugenre"))!!.children().joinToString { it.ownText() }
        for (item in root.selectFirst(".infotable tbody")!!.children()) {
            when (item.selectFirst("td:nth-child(1)")!!.ownText()) {
                "Auteur" -> item.parseAuthorsTo(this)
                "Artiste" -> item.parseAuthorsTo(this)
            }
        }

        status = root.selectFirst(statusMangaSelector())!!.ownText().let {
            when (it) {
                "En Cours" -> SManga.ONGOING
                "Terminé" -> SManga.COMPLETED
                "En Pause" -> SManga.ON_HIATUS
                "Abandonné" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun Element.parseAuthorsTo(manga: SManga) {
        when (selectFirst("td:nth-child(1)")!!.ownText()) {
            "Auteur" -> { // Use <a>
                val authors = select(Evaluator.Tag("a"))
                val text = authors.map { it.ownText().replace(",", "") }
                val count = authors.size
                when (count) {
                    0 -> return
                    1 -> {
                        manga.author = text[0]
                        return
                    }
                }
            }
            "Artiste:" -> { // Use <td:nth-child(2)>.ownText()
                var text = selectFirst("td:nth-child(2)")!!.ownText().replace(",", "")
                manga.artist = text
            }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(mangaUrl: String, type: String): Request =
        GET(baseUrl + mangaUrl, headers)

    override fun parseChapterElements(response: Response, isVolume: Boolean): List<Element> {
        TODO("Not yet implemented")
    }

    override val chapterType = ""
    override val volumeType = ""

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map(::parseChapterList)
    }

    private fun parseChapterList(response: Response): List<SChapter> {
        val document = response.use { it.asJsoup() }

        return document.select(chapterListSelector())
            .map(::chapterFromElement)
    }

    private fun chapterListSelector(): String = "#chapterlist li"

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(attr("href"))
            name = selectFirst(Evaluator.Class("chaptername"))?.text() ?: (
                selectFirst(Evaluator.Tag("span"))?.text()
                    ?: text()
                )
            date_upload = selectFirst(Evaluator.Class("chapterdate"))?.text()?.let { parseChapterDate(it) } ?: 0
        }
    }

    // =============================== Pages ================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val url = "$baseUrl${chapter.url}"
        client.newCall(GET(url)).execute().let(::pageListParse)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = Jsoup.parse(response.body!!.string())

        return document.select("#readerarea img").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgAttr())
        }
    }

    // ============================= Utilities ==============================

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    private fun parseChapterDate(date: String): Long {
        // Uppercase the first letter of the string
        val formattedDate = date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRANCE) else it.toString() }
        return SimpleDateFormat("MMMM d, yyyy", Locale.FRANCE).parse(formattedDate).time
    }
}
