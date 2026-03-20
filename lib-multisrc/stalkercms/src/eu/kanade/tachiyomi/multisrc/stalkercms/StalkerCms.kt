package eu.kanade.tachiyomi.multisrc.stalkercms

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

abstract class StalkerCms(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String = "pt-BR",
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    protected open val popularMangaPath = "/manga/todos/"

    /** Latest página 2+ usa este path com ?page=N. Null desativa load-more. */
    protected open val latestUpdatesLoadMorePath: String? = "/manga/ajax/load-more-releases/"

    /** Seletor do botão "Carregar Mais" na página 1 para hasNextPage (quando load-more ativo). */
    protected open val latestUpdatesHasNextPageSelector: String? = "#load-more-btn"

    protected open val mangaCardSelector = ".comics-grid a.comic-card-link, div.manga-card-simple"

    protected open val hasNextPageSelector = ".page-link[aria-label=Próxima]:not(disabled)"

    protected open val detailsTitleSelector = "h1"
    protected open val detailsThumbnailSelector = ".sidebar-cover-image img"
    protected open val detailsDescriptionSelector = ".manga-description"
    protected open val detailsGenreSelector = "a.genre-tag"
    protected open val detailsStatusSelector = ".status-tag"

    protected open val chapterListSelector = ".chapter-item-list a.chapter-link"
    protected open val chapterNameSelector = ".chapter-number"
    protected open val chapterDateSelector = ".chapter-date"

    protected open val pageListSelector = ".chapter-image-canvas"
    protected open val pageImageAttr = "data-src-url"

    protected open val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

    protected open fun parseStatus(status: String?): Int = when (status?.trim()?.lowercase()) {
        "em andamento" -> SManga.ONGOING
        "concluído" -> SManga.COMPLETED
        "hiato" -> SManga.ON_HIATUS
        "cancelado" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Popular ==================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl$popularMangaPath?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // ============================== Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request = if (page > 1 && latestUpdatesLoadMorePath != null) {
        GET("$baseUrl$latestUpdatesLoadMorePath?page=$page", headers)
    } else {
        GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val loadMorePath = latestUpdatesLoadMorePath
        if (loadMorePath != null && response.request.url.toString().contains(loadMorePath)) {
            val dto = response.parseAs<LoadMoreReleasesDto>()
            val document = Jsoup.parse(dto.html, baseUrl)
            val mangas = document.select(mangaCardSelector).map(::mangaFromElement)
            return MangasPage(mangas, dto.hasNext)
        }
        val document = response.asJsoup()
        val mangas = document.select(mangaCardSelector).map(::mangaFromElement)
        val hasNextPage = if (loadMorePath != null && latestUpdatesHasNextPageSelector != null) {
            document.selectFirst(latestUpdatesHasNextPageSelector!!) != null
        } else {
            document.selectFirst(hasNextPageSelector) != null
        }
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/live-search/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<SearchDto>().results.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Details ==================================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(detailsTitleSelector)!!.text()
            thumbnail_url = document.selectFirst(detailsThumbnailSelector)?.absUrl("src")
            description = document.selectFirst(detailsDescriptionSelector)?.text()
            genre = document.select(detailsGenreSelector).joinToString { it.text() }
            status = parseStatus(document.selectFirst(detailsStatusSelector)?.text())
            initialized = true
        }
    }

    // ============================== Chapters =================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val urlBuilder = getMangaUrl(manga).toHttpUrl().newBuilder()
        buildList {
            var page = 1
            do {
                val url = urlBuilder
                    .setQueryParameter("page", (page++).toString())
                    .build()
                val document = client.newCall(GET(url, headers)).execute().asJsoup()
                addAll(chapterListParse(document))
            } while (document.selectFirst(hasNextPageSelector) != null)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    protected open fun chapterListParse(document: Document): List<SChapter> = document.select(chapterListSelector).map(::chapterFromElement)

    protected open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst(chapterNameSelector)!!.text()
        date_upload = dateFormat.tryParse(element.selectFirst(chapterDateSelector)?.ownText())
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // ============================== Pages ====================================

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(pageListSelector)
        .mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl(pageImageAttr))
        }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers ===================================

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(mangaCardSelector).map(::mangaFromElement)
        val hasNextPage = document.selectFirst(hasNextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(
            element.absUrl("href").ifBlank {
                element.selectFirst("a")!!.absUrl("href")
            },
        )
    }
}
