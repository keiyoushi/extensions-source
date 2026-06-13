package eu.kanade.tachiyomi.extension.tr.sleptmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response

class SleptManga : HttpSource() {

    override val name = "Slept Manga"
    override val baseUrl = "https://sleptmanga.com.tr"
    override val lang = "tr"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse?sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val elements = document.select("a.group:has(article)")

        val mangas = elements
            .filterNot { it.absUrl("href").toHttpUrlOrNull()?.pathSegments?.firstOrNull() == "novel" }
            .mapNotNull { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    title = element.selectFirst("h3")?.text()?.takeIf { it.isNotEmpty() }
                        ?: element.selectFirst("img")?.attr("alt")?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }

        val hasNextPage = document.extractNextJs<PaginationDto>()?.hasNextPage
            ?: (elements.size >= 20)

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("page", page.toString())

            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }

            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("sort", it.toUriPart())
            }
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
                ?.takeIf { it != "all" }
                ?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()
                ?.takeIf { it != "all" }
                ?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<GenreFilterGroup>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("genres", it.value) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val series = response.extractNextJs<SeriesDto>()
            ?: throw Exception("Manga detayları bulunamadı")

        return series.toSManga(baseUrl)
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = response.extractNextJs<SeriesDto>()
            ?: return emptyList()

        val encodedPath = response.request.url.encodedPath.removeSuffix("/")

        return series.toSChapterList(encodedPath)
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<ChapterDataDto>()
            ?: return emptyList()

        return data.toPageList(baseUrl)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        Filter.Separator(),
        GenreFilterGroup(getGenreList()),
    )
}
