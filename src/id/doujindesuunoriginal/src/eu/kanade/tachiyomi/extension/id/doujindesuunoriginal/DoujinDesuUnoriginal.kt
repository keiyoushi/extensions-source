package eu.kanade.tachiyomi.extension.id.doujindesuunoriginal

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.lang.UnsupportedOperationException

private const val DOMAIN = "v2.doujindesu.fun"

class DoujinDesuUnoriginal : HttpSource() {
    override val name = "DoujinDesu (Unoriginal)"
    override val lang = "id"
    override val baseUrl = "https://$DOMAIN"
    override val supportsLatest = true

    override val client = network.client

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .set("Rsc", "1")
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotBlank() && query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host == DOMAIN && url.pathSegments[0] == "manga") {
                val slug = url.pathSegments[1]
                val tmpManga = SManga.create().apply { this.url = slug }
                return fetchMangaDetails(tmpManga).map { MangasPage(listOf(it), false) }
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.status?.also { addQueryParameter("status", it) }
                    is TypeFilter -> filter.type?.also { addQueryParameter("type", it) }
                    is SortFilter -> filter.sort?.also { addQueryParameter("order", it) }
                    is GenreFilter -> filter.genre?.also { addQueryParameter("genre", it) }
                    else -> {}
                }
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, rscHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")?.toInt() ?: 1
        val data = response.extractNextJs<MangaList>()

        val mangas = data?.mangas.orEmpty().map { it.toSManga() }
        val hasNextPage = data?.hasNextPage(page) ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.extractNextJs<MangaDetails>() ?: throw Exception("Failed to parse manga details")
        return data.manga.toSManga()
    }

    // ============================ Chapter List ============================

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments.last()
        val data = response.extractNextJs<ChaptersList>()

        return data?.chapters.orEmpty().map { it.toSChapter(slug) }
    }

    // ============================= Page List ==============================

    override fun pageListRequest(chapter: SChapter): Request {
        val pathSegments = chapter.url.split("/")
        if (pathSegments.size < 4) return GET("$baseUrl/api", headers)

        val mangaSlug = pathSegments[2]
        val chapterSlug = pathSegments[3]
        return GET("$baseUrl/api/read/$mangaSlug/$chapterSlug", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun pageListParse(response: Response): List<Page> {
        val data = runCatching { response.parseAs<ReaderData>() }.getOrNull()

        return data?.data?.chapter?.images.orEmpty().mapIndexed { index, img ->
            Page(index, imageUrl = img)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filter dapat digunakan bersamaan dengan pencarian teks"),
        Filter.Separator(),
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )
}
