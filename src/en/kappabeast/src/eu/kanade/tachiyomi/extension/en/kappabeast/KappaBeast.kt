package eu.kanade.tachiyomi.extension.en.kappabeast

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable

class KappaBeast : HttpSource() {
    private val domain = "kappabeast.com"
    override val baseUrl = "https://$domain"
    override val lang = "en"
    override val supportsLatest = true
    override val name = "Kappa Beast"
    override val versionId = 2

    private val cdnUrl = "https://strapi.$domain"
    private val apiUrl = "$cdnUrl/api"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList().apply { firstInstance<SortFilter>().state = 0 })

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList().apply { firstInstance<SortFilter>().state = 1 })

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        fun Builder.addFilter(param: String, filter: SelectFilter) = filter.value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }

        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter($$"filters[title][$containsi]", query)

            addQueryParameter("pagination[page]", page.toString())
            addQueryParameter("pagination[pageSize]", "20")
            addQueryParameter("populate[media][populate]", "*")
            addQueryParameter("populate[category][fields][0]", "name")

            if (filters.isNotEmpty()) {
                addFilter($$"filters[category][name][$eq]", filters.firstInstance<GenreFilter>())
                addFilter($$"filters[manga_status][$eq]", filters.firstInstance<StatusFilter>())
                addFilter($$"filters[type][$eq]", filters.firstInstance<TypeFilter>())
                addFilter("sort[0]", filters.firstInstance<SortFilter>())
            }
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        SortFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.meta.pagination.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = "$baseUrl/${manga.url}".toHttpUrl().pathSegments.first()
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter($$"filters[slug][$eq]", slug)
            .addQueryParameter("populate[media][populate]", "*")
            .addQueryParameter("populate[category][fields][0]", "name")
            .addQueryParameter("pagination[pageSize]", "1")
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SearchResponse>().data.first().toSManga(cdnUrl)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val documentId = "$baseUrl/${manga.url}".toHttpUrl().fragment
        if (documentId.isNullOrBlank()) {
            throw Exception("Migrate from $name to $name.")
        }
        val chapters = mutableListOf<SChapter>()
        var page = 1
        var result: ChapterResponse

        do {
            val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
                .addQueryParameter($$"filters[manga][documentId][$eq]", documentId)
                .addQueryParameter("populate[pages][populate]", "*")
                .addQueryParameter("populate", "manga")
                .addQueryParameter("sort[0]", "number:desc")
                .addQueryParameter("pagination[page]", page.toString())
                .addQueryParameter("pagination[pageSize]", "100")
                .build()

            result = client.newCall(GET(url, headers)).execute().parseAs<ChapterResponse>()
            chapters += result.data.map { it.toSChapter() }
            page++
        } while (result.meta.pagination.hasNextPage())

        return Observable.just(chapters)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl()
        val documentId = parts.fragment
        val chapterNum = parts.pathSegments[1]
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter($$"filters[manga][documentId][$eq]", documentId)
            .addQueryParameter($$"filters[number][$eq]", chapterNum)
            .addQueryParameter("populate[pages][populate]", "*")
            .addQueryParameter("populate", "manga")
            .addQueryParameter("sort[0]", "number:desc")
            .addQueryParameter("pagination[pageSize]", "1")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterResponse>().data.first().htmlContent ?: throw Exception("This chapter contains no pages.")
        return Jsoup.parseBodyFragment(result).select("div.separator > a").mapIndexed { i, url ->
            Page(i, imageUrl = url.absUrl("href").toHttpUrl().newBuilder().setPathSegment(4, "s0").build().toString())
        }
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
