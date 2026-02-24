package eu.kanade.tachiyomi.extension.pt.pizzariascan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Pizzariascan : ParsedHttpSource() {

    override val id: Long = 3359822911747375789

    override val name = "Pizzaria Scan"

    override val baseUrl = "https://pizzariacomics.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val dateFormat by lazy {
        SimpleDateFormat(
            "dd 'de' MMMMM 'de' yyyy",
            Locale("pt", "BR"),
        )
    }

    protected fun pagePathSegment(page: Int): String = if (page > 1) "page/$page/" else ""

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/series/${pagePathSegment(page)}?order=popular")

    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/series/${pagePathSegment(page)}?order=update")

    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    // ============================== Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/series/".toHttpUrl().newBuilder()

        if (page > 1) {
            urlBuilder.addPathSegment("page")
            urlBuilder.addPathSegment(page.toString())
        }

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("title", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SelectFilter<*> -> {
                    val value = filter.selected ?: return@forEach
                    urlBuilder.addQueryParameter(filter.query, value.toString())
                }

                is CheckBoxGroup<*> -> {
                    filter.checked.forEach { value ->
                        urlBuilder.addQueryParameter(filter.query, value.toString())
                    }
                }

                else -> {}
            }
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.substringAfter(PREFIX_SEARCH)
            return fetchMangaDetails(SManga.create().apply { url = "/$slug" })
                .map { manga -> MangasPage(listOf(manga), false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector(): String = "#main div.grid a"
    override fun searchMangaNextPageSelector(): String = "div.pagination .current + a"
    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("#manga-info h1")!!.text()
        thumbnail_url = document.selectFirst("head meta[property='og:image']")?.attr("content")
        genre = document.select("a[itemprop=genre]").joinToString { it.text() }
        author = document.selectFirst("strong:contains(Autor) + p.text-muted-foreground")?.text()
        document.selectFirst("#manga-info div > span.text-white.rounded")?.text()?.let {
            status = when (it.lowercase()) {
                "em andamento" -> SManga.ONGOING
                "finalizado" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        description = document.selectFirst("#manga-info div.text-base.leading-relaxed")?.text()
        setUrlWithoutDomain(document.location())
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val document = client.newCall(mangaDetailsRequest(manga)).execute().asJsoup()

        val chapterList = document.selectFirst("#chapter_list")!!
        val postId = chapterList.attr("data-post-id")
        val count = chapterList.attr("data-count")

        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val response = client.newCall(chapterListRequest(postId, count, page++)).execute()
            val chapterGroup = chapterListParse(response).also {
                chapters += it
            }
        } while (chapterGroup.isNotEmpty())

        return Observable.just(chapters)
    }

    private fun chapterListRequest(postId: String, count: String, page: Int): Request {
        val form = FormBody.Builder().apply {
            add("action", "load_chapters")
            add("post_id", postId)
            add("count", count)
            add("paged", page.toString())
            add("order", "DESC")
        }.build()
        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, form)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapter_list ul li a")
            .map { element ->
                SChapter.create().apply {
                    name = element.selectFirst("span.text-xs")!!.ownText()
                    chapter_number = name.split(" ").last().toFloatOrNull() ?: 0F
                    date_upload = dateFormat.tryParse(element.selectFirst("span.block")?.text())
                    setUrlWithoutDomain(element.absUrl("href"))
                }
            }
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()
    override fun chapterListSelector() = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun pageListParse(document: Document): List<Page> = document.select("div.reader-area img").mapIndexed { i, img ->
        Page(i, imageUrl = img.attr("abs:src"))
    }

    override fun imageUrlParse(document: Document) = ""

    // =============================== Filters ===============================

    override fun getFilterList() = getFilters()

    companion object {
        const val PREFIX_SEARCH = "id:"
        val SPACE_REGEX = """\s+""".toRegex()
    }
}
