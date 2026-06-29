package eu.kanade.tachiyomi.extension.es.hentaihall

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class HentaiHall : HttpSource() {

    override val name = "HentaiHall"

    override val baseUrl = "https://hentaihall.com"

    private val apiUrl = "https://hentaihallbackend-production.up.railway.app"

    override val lang = "es"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 20
                maxRequestsPerHost = 10
            },
        )
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private val apiHeaders by lazy {
        headersBuilder()
            .set("Accept", "application/json, text/plain, */*")
            .build()
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/manhwa/library?buscar=&quebusca=nombre&order_item=seguir&order_dir=desc&page=${page - 1}&generes=", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<PageDto>()
        return MangasPage(result.data.map { it.toSManga() }, result.next)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/manhwa/library?buscar=&quebusca=nombre&order_item=creacion&order_dir=desc&page=${page - 1}&generes=", apiHeaders)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manhwa/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("buscar", query)
            addQueryParameter("page", (page - 1).toString())

            val searchBy = filters.firstInstanceOrNull<SearchByFilter>()?.selectedValue() ?: "nombre"
            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            val sortBy = sortFilter?.selectedValue() ?: "seguir"
            val sortDir = if (sortFilter?.state?.ascending == true) "asc" else "desc"
            val genres = filters.firstInstanceOrNull<GenreFilterGroup>()?.state
                ?.filter { it.state }
                ?.joinToString("_") { it.name }
                ?: ""

            addQueryParameter("quebusca", searchBy)
            addQueryParameter("order_item", sortBy)
            addQueryParameter("order_dir", sortDir)
            addQueryParameter("generes", genres)
        }.build()

        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = getFilters()

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manhwa/see/${manga.url}", apiHeaders)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/content/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsDto>().toSManga()

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        // Because HentaiHall functions essentially as a one-shot source without unique chapter entries,
        // we generate the only existing chapter object strictly out of the same Details JSON data.
        return listOf(response.parseAs<DetailsDto>().toSChapter())
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/manhwa/chapter/${chapter.url}", apiHeaders)

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterDto>()
        return data.chapter.filter { it.isNotBlank() }.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageRequest(page: Page): Request {
        // Requesting '*/*' disables the CDN's WebP compression and serves the original highest quality JPEGs
        val imageHeaders = headersBuilder()
            .set("Accept", "*/*")
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
