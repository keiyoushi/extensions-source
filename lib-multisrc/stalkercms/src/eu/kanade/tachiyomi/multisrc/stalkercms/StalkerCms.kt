package eu.kanade.tachiyomi.multisrc.stalkercms

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class StalkerCms : KeiSource() {

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

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

    protected open val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT)

    protected open fun parseStatus(status: String?): Int = when (status?.trim()?.lowercase()) {
        "em andamento" -> SManga.ONGOING
        "concluído" -> SManga.COMPLETED
        "hiato" -> SManga.ON_HIATUS
        "cancelado" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Popular ==================================

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get("$baseUrl$popularMangaPath?page=$page").asJsoup())

    // ============================== Latest ===================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val loadMorePath = latestUpdatesLoadMorePath
        if (page > 1 && loadMorePath != null) {
            val dto = client.get("$baseUrl$loadMorePath?page=$page").parseAs<LoadMoreReleasesDto>()
            val document = Jsoup.parse(dto.html, baseUrl)
            val mangas = document.select(mangaCardSelector).map(::mangaFromElement)
            return MangasPage(mangas, dto.hasNext)
        }
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(mangaCardSelector).map(::mangaFromElement)
        val hasNextPage = if (loadMorePath != null && latestUpdatesHasNextPageSelector != null) {
            document.selectFirst(latestUpdatesHasNextPageSelector!!) != null
        } else {
            document.selectFirst(hasNextPageSelector) != null
        }
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/live-search/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val mangas = client.get(url).parseAs<SearchDto>().results.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Details ==================================

    // details and first chapter page come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val urlBuilder = getMangaUrl(manga).toHttpUrl().newBuilder()
        val firstPage = client.get(urlBuilder.setQueryParameter("page", "1").build()).asJsoup()
        val details = mangaDetailsParse(firstPage)
        val chapterList = if (fetchChapters) {
            buildList {
                var document = firstPage
                var page = 1
                while (true) {
                    addAll(chapterListParse(document))
                    if (document.selectFirst(hasNextPageSelector) == null) break
                    document = client.get(urlBuilder.setQueryParameter("page", (++page).toString()).build()).asJsoup()
                }
            }
        } else {
            chapters
        }
        return SMangaUpdate(details, chapterList)
    }

    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(detailsTitleSelector)!!.text()
        thumbnail_url = document.selectFirst(detailsThumbnailSelector)?.absUrl("src")
        description = document.selectFirst(detailsDescriptionSelector)?.text()
        genre = document.select(detailsGenreSelector).joinToString { it.text() }
        status = parseStatus(document.selectFirst(detailsStatusSelector)?.text())
        initialized = true
    }

    // ============================== Chapters =================================

    protected open fun chapterListParse(document: Document): List<SChapter> = document.select(chapterListSelector).map(::chapterFromElement)

    protected open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst(chapterNameSelector)!!.text()
        date_upload = dateFormat.tryParseDate(element.selectFirst(chapterDateSelector)?.ownText())
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // ============================== Pages ====================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select(pageListSelector)
        .mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl(pageImageAttr))
        }

    // ============================== Helpers ===================================

    private fun mangaListParse(document: Document): MangasPage {
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
