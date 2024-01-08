package eu.kanade.tachiyomi.extension.pt.saikaiscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class SaikaiScan : HttpSource() {

    override val name = SOURCE_NAME

    override val baseUrl = "https://saikaiscans.net"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(API_URL.toHttpUrl(), 1, 2)
        .rateLimitHost(IMAGE_SERVER_URL.toHttpUrl(), 1, 1)
        .build()

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("sortProperty", "pageviews")
            .addQueryParameter("sortDirection", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("relationships", "language,type,format")
            .build()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SaikaiScanPaginatedStoriesDto>()

        val mangaList = result.data!!.map(SaikaiScanStoryDto::toSManga)

        return MangasPage(mangaList, result.hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/lancamentos".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("relationships", "language,type,format,latestReleases.separator")
            .build()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("q", query)
            .addQueryParameter("sortProperty", "pageViews")
            .addQueryParameter("sortDirection", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("relationships", "language,type,format")

        filters.filterIsInstance<UrlQueryFilter>()
            .forEach { it.addQueryParameter(apiEndpointUrl) }

        return GET(apiEndpointUrl.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val storySlug = manga.url.substringAfterLast("/")

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("slug", storySlug)
            .addQueryParameter("per_page", "1")
            .addQueryParameter("relationships", "language,type,format,artists,status")
            .build()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SaikaiScanPaginatedStoriesDto>()

        return result.data!![0].toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val storySlug = manga.url.substringAfterLast("/")

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("slug", storySlug)
            .addQueryParameter("per_page", "1")
            .addQueryParameter("relationships", "releases")
            .build()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<SaikaiScanPaginatedStoriesDto>()
        val story = result.data!![0]

        return story.releases
            .filter { it.isActive == 1 }
            .map { it.toSChapter(story.slug) }
            .sortedByDescending(SChapter::chapter_number)
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val releaseId = chapter.url
            .substringBeforeLast("/")
            .substringAfterLast("/")

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/releases/$releaseId".toHttpUrl().newBuilder()
            .addQueryParameter("relationships", "releaseImages")
            .build()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<SaikaiScanReleaseResultDto>()

        return result.data?.releaseImages.orEmpty().mapIndexed { i, obj ->
            Page(i, "", "$IMAGE_SERVER_URL/${obj.image}")
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    // fetch('https://api.saikai.com.br/api/genres')
    //     .then(res => res.json())
    //     .then(res => console.log(res.data.map(g => `Genre("${g.name}", ${g.id})`).join(',\n')))
    private fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", 1),
        Genre("Adulto", 23),
        Genre("Artes Marciais", 84),
        Genre("Aventura", 2),
        Genre("Comédia", 15),
        Genre("Drama", 14),
        Genre("Ecchi", 19),
        Genre("Esportes", 42),
        Genre("eSports", 25),
        Genre("Fantasia", 3),
        Genre("Ficção Cientifica", 16),
        Genre("Histórico", 37),
        Genre("Horror", 27),
        Genre("Isekai", 52),
        Genre("Josei", 40),
        Genre("Luta", 68),
        Genre("Magia", 11),
        Genre("Militar", 76),
        Genre("Mistério", 57),
        Genre("MMORPG", 80),
        Genre("Música", 82),
        Genre("One-shot", 51),
        Genre("Psicológico", 34),
        Genre("Realidade Vitual", 18),
        Genre("Reencarnação", 43),
        Genre("Romance", 9),
        Genre("RPG", 61),
        Genre("Sci-fi", 58),
        Genre("Seinen", 21),
        Genre("Shoujo", 35),
        Genre("Shounen", 26),
        Genre("Slice of Life", 38),
        Genre("Sobrenatural", 74),
        Genre("Suspense", 63),
        Genre("Tragédia", 22),
        Genre("VRMMO", 17),
        Genre("Wuxia", 6),
        Genre("Xianxia", 7),
        Genre("Xuanhuan", 48),
        Genre("Yaoi", 41),
        Genre("Yuri", 83),
    )

    // fetch('https://api.saikai.com.br/api/countries?hasStories=1')
    //     .then(res => res.json())
    //     .then(res => console.log(res.data.map(g => `Country("${g.name}", ${g.id})`).join(',\n')))
    private fun getCountryList(): List<Country> = listOf(
        Country("Todas", 0),
        Country("Brasil", 32),
        Country("China", 45),
        Country("Coréia do Sul", 115),
        Country("Espanha", 199),
        Country("Estados Unidos da América", 1),
        Country("Japão", 109),
        Country("Portugal", 173),
    )

    // fetch('https://api.saikai.com.br/api/countries?hasStories=1')
    //     .then(res => res.json())
    //     .then(res => console.log(res.data.map(g => `Country("${g.name}", ${g.id})`).join(',\n')))
    private fun getStatusList(): List<Status> = listOf(
        Status("Todos", 0),
        Status("Cancelado", 5),
        Status("Concluído", 1),
        Status("Dropado", 6),
        Status("Em Andamento", 2),
        Status("Hiato", 4),
        Status("Pausado", 3),
    )

    private fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty("Título", "title"),
        SortProperty("Quantidade de capítulos", "releases_count"),
        SortProperty("Visualizações", "pageviews"),
        SortProperty("Data de criação", "created_at"),
    )

    override fun getFilterList(): FilterList = FilterList(
        CountryFilter(getCountryList()),
        StatusFilter(getStatusList()),
        SortByFilter(getSortProperties()),
        GenreFilter(getGenreList()),
    )

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    companion object {
        const val SOURCE_NAME = "Saikai Scan"

        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        private const val COMIC_FORMAT_ID = "2"
        private const val PER_PAGE = "12"

        private const val API_URL = "https://api.saikaiscans.net"
        const val IMAGE_SERVER_URL = "https://s3-alpha.saikaiscans.net"
    }
}
