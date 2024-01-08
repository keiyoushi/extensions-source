package eu.kanade.tachiyomi.extension.pt.taosect

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TaoSect : HttpSource() {

    override val name = "Tao Sect"

    override val baseUrl = "https://taosect.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    private val json: Json by injectLazy()

    private val apiHeaders: Headers by lazy { apiHeadersBuilder().build() }

    private var latestIds: List<String> = emptyList()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    private fun apiHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", ACCEPT_JSON)

    override fun popularMangaRequest(page: Int): Request {
        val apiUrl = "$baseUrl/$API_BASE_PATH/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderby", "views")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PROJECTS_PER_PAGE.toString())
            .addQueryParameter("_fields", DEFAULT_FIELDS)
            .toString()

        return GET(apiUrl, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<TaoSectProjectDto>>()

        val projectList = result.map(::popularMangaFromObject)

        val currentPage = response.request.url.queryParameter("page")
            .orEmpty().toIntOrNull() ?: 1
        val lastPage = response.headers["X-Wp-TotalPages"]!!.toInt()
        val hasNextPage = currentPage < lastPage

        return MangasPage(projectList, hasNextPage)
    }

    private fun popularMangaFromObject(obj: TaoSectProjectDto): SManga = SManga.create().apply {
        title = Parser.unescapeEntities(obj.title!!.rendered, true)
        thumbnail_url = obj.thumbnail
        setUrlWithoutDomain(obj.link!!)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$baseUrl/$API_BASE_PATH/capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderby", "date")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", (PROJECTS_PER_PAGE * 2).toString())
            .addQueryParameter("_fields", "post_id")
            .toString()

        return GET(apiUrl, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<List<TaoSectChapterDto>>()

        if (result.isEmpty()) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val currentPage = response.request.url.queryParameter("page")!!.toInt()
        val lastPage = response.headers["X-Wp-TotalPages"]!!.toInt()
        val hasNextPage = currentPage < lastPage

        if (currentPage == 1) {
            latestIds = emptyList()
        }

        val projectIds = result
            .map { it.projectId!! }
            .distinct()
            .filterNot { latestIds.contains(it) }

        latestIds = latestIds + projectIds

        if (projectIds.isEmpty()) {
            return MangasPage(emptyList(), hasNextPage)
        }

        val projectsApiUrl = "$baseUrl/$API_BASE_PATH/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("include", projectIds.joinToString(","))
            .addQueryParameter("per_page", projectIds.size.toString())
            .addQueryParameter("orderby", "include")
            .addQueryParameter("_fields", DEFAULT_FIELDS)
            .toString()
        val projectsRequest = GET(projectsApiUrl, apiHeaders)
        val projectsResponse = client.newCall(projectsRequest).execute()
        val projectsResult = projectsResponse.parseAs<List<TaoSectProjectDto>>()

        val projectList = projectsResult.map(::popularMangaFromObject)

        return MangasPage(projectList, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(SLUG_PREFIX_SEARCH) && query.removePrefix(SLUG_PREFIX_SEARCH).isNotBlank()) {
            return mangaDetailsRequest(query.removePrefix(SLUG_PREFIX_SEARCH))
        }

        val apiUrl = "$baseUrl/$API_BASE_PATH/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PROJECTS_PER_PAGE.toString())
            .addQueryParameter("_fields", DEFAULT_FIELDS)

        if (query.isNotEmpty()) {
            apiUrl.addQueryParameter("search", query)
        }

        filters.filterIsInstance<QueryParameterFilter>()
            .forEach { it.toQueryParameter(apiUrl, query) }

        return GET(apiUrl.toString(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request = mangaDetailsRequest(manga.url)

    private fun mangaDetailsRequest(mangaUrl: String): Request {
        val projectSlug = mangaUrl
            .substringAfterLast("projeto/")
            .substringBefore("/")

        val apiUrl = "$baseUrl/$API_BASE_PATH/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "1")
            .addQueryParameter("slug", projectSlug)
            .addQueryParameter("_fields", "title,informacoes,content,thumbnail,link")
            .toString()

        return GET(apiUrl, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<List<TaoSectProjectDto>>()

        if (result.isEmpty()) {
            throw Exception(PROJECT_NOT_FOUND)
        }

        val project = result[0]

        return SManga.create().apply {
            title = Parser.unescapeEntities(project.title!!.rendered, true)
            author = project.info!!.script
            artist = project.info.art
            genre = project.info.genres.joinToString { it.name }
            status = project.info.status!!.name.toStatus()
            description = Jsoup.parse(project.content!!.rendered).text() +
                "\n\nTítulo original: " + project.info.originalTitle +
                "\nSerialização: " + project.info.serialization
            thumbnail_url = project.thumbnail
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val projectSlug = manga.url
            .substringAfterLast("projeto/")
            .substringBefore("/")

        val apiUrl = "$baseUrl/$API_BASE_PATH/capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("projeto", projectSlug)
            .addQueryParameter("per_page", "1000")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderby", "sequencia")
            .addQueryParameter("_fields", "nome_capitulo,post_id,slug,data_insercao")
            .toString()

        return GET(apiUrl, apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<List<TaoSectChapterDto>>()

        if (result.isEmpty()) {
            throw Exception(CHAPTERS_NOT_FOUND)
        }

        // Count the project views, requested by the scanlator.
        val countViewRequest = countProjectViewRequest(result[0].projectId!!)
        runCatching { client.newCall(countViewRequest).execute().close() }

        val projectSlug = response.request.url.queryParameter("projeto")!!

        return result.map { chapterFromObject(it, projectSlug) }
    }

    private fun chapterFromObject(obj: TaoSectChapterDto, projectSlug: String): SChapter = SChapter.create().apply {
        name = obj.name
        scanlator = this@TaoSect.name
        date_upload = obj.date.toDate()
        url = "/leitor-online/projeto/$projectSlug/${obj.slug}/"
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val projectSlug = chapter.url
            .substringAfter("projeto/")
            .substringBefore("/")
        val chapterSlug = chapter.url
            .removeSuffix("/")
            .substringAfterLast("/")

        val apiUrl = "$baseUrl/$API_BASE_PATH/capitulos/".toHttpUrl().newBuilder()
            .addPathSegment(projectSlug)
            .addPathSegment(chapterSlug)
            .addQueryParameter("_fields", "id_capitulo,paginas,post_id")
            .toString()

        return GET(apiUrl, apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<TaoSectChapterDto>()

        if (result.pages.isEmpty()) {
            return emptyList()
        }

        val apiUrlPaths = response.request.url.pathSegments
        val projectSlug = apiUrlPaths[4]
        val chapterSlug = apiUrlPaths[5]

        val chapterUrl = "$baseUrl/leitor-online/projeto/$projectSlug/$chapterSlug"

        val pages = result.pages.mapIndexed { i, pageUrl ->
            Page(i, chapterUrl, pageUrl)
        }

        // Count the project and chapter views, requested by the scanlator.
        val countViewRequest = countProjectViewRequest(result.projectId!!, result.id)
        runCatching { client.newCall(countViewRequest).execute().close() }

        // Check if the pages have exceeded the view limit of Google Drive.
        val firstPage = pages[0]

        val hasExceededViewLimit = runCatching {
            val firstPageRequest = imageRequest(firstPage)

            client.newCall(firstPageRequest).execute().use {
                val isHtml = it.headers["Content-Type"]!!.contains("text/html")

                GoogleDriveResponse(!isHtml && it.isSuccessful, it.code)
            }
        }

        val defaultResponse = GoogleDriveResponse(false, GD_BACKEND_ERROR)
        val googleDriveResponse = hasExceededViewLimit.getOrDefault(defaultResponse)

        if (!googleDriveResponse.isValid) {
            throw Exception(googleDriveResponse.errorMessage)
        }

        return pages
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun countProjectViewRequest(projectId: String, chapterId: String? = null): Request {
        val formBodyBuilder = FormBody.Builder()
            .add("action", "update_views_v2")
            .add("projeto", projectId)

        if (chapterId != null) {
            formBodyBuilder.add("capitulo", chapterId)
        }

        val formBody = formBodyBuilder.build()

        val newHeaders = headersBuilder()
            .add("Content-Length", formBody.contentLength().toString())
            .add("Content-Type", formBody.contentType().toString())
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", newHeaders, formBody)
    }

    override fun getFilterList(): FilterList = FilterList(
        CountryFilter(getCountryList()),
        StatusFilter(getStatusList()),
        GenreFilter(getGenreList()),
        SortFilter(SORT_LIST, DEFAULT_ORDERBY),
        FeaturedFilter(),
        NsfwFilter(),
    )

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    private fun String.toStatus() = when (this) {
        "Ativos" -> SManga.ONGOING
        "Finalizados", "Oneshots" -> SManga.COMPLETED
        "Cancelados" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun getCountryList(): List<Tag> = listOf(
        Tag("59", "China"),
        Tag("60", "Coréia do Sul"),
        Tag("13", "Japão"),
    )

    private fun getStatusList(): List<Tag> = listOf(
        Tag("3", "Ativo"),
        Tag("5", "Cancelado"),
        Tag("4", "Finalizado"),
        Tag("6", "One-shot"),
    )

    private fun getGenreList(): List<Tag> = listOf(
        Tag("31", "4Koma"),
        Tag("24", "Ação"),
        Tag("84", "Adulto"),
        Tag("21", "Artes Marciais"),
        Tag("25", "Aventura"),
        Tag("26", "Comédia"),
        Tag("66", "Culinária"),
        Tag("78", "Doujinshi"),
        Tag("22", "Drama"),
        Tag("12", "Ecchi"),
        Tag("30", "Escolar"),
        Tag("76", "Esporte"),
        Tag("23", "Fantasia"),
        Tag("29", "Harém"),
        Tag("75", "Histórico"),
        Tag("83", "Horror"),
        Tag("18", "Isekai"),
        Tag("20", "Light Novel"),
        Tag("61", "Manhua"),
        Tag("56", "Psicológico"),
        Tag("7", "Romance"),
        Tag("27", "Sci-fi"),
        Tag("28", "Seinen"),
        Tag("55", "Shoujo"),
        Tag("54", "Shounen"),
        Tag("19", "Slice of life"),
        Tag("17", "Sobrenatural"),
        Tag("57", "Tragédia"),
        Tag("62", "Webtoon"),
    )

    private data class GoogleDriveResponse(val isValid: Boolean, val code: Int) {
        val errorMessage: String
            get() = when (code) {
                GD_SHARING_RATE_LIMIT_EXCEEDED -> EXCEEDED_GOOGLE_DRIVE_VIEW_LIMIT
                else -> GOOGLE_DRIVE_UNAVAILABLE
            }
    }

    companion object {
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json"
        private val USER_AGENT = "Tachiyomi " + System.getProperty("http.agent")

        const val SLUG_PREFIX_SEARCH = "slug:"

        private const val API_BASE_PATH = "wp-json/wp/v2"
        private const val PROJECTS_PER_PAGE = 18
        private const val DEFAULT_ORDERBY = 3
        private const val DEFAULT_FIELDS = "title,thumbnail,link"
        private const val PROJECT_NOT_FOUND = "Projeto não encontrado."
        private const val CHAPTERS_NOT_FOUND = "Capítulos não encontrados."
        private const val EXCEEDED_GOOGLE_DRIVE_VIEW_LIMIT = "Limite de visualizações atingido " +
            "no Google Drive. Tente novamente mais tarde."
        private const val GOOGLE_DRIVE_UNAVAILABLE = "O Google Drive está indisponível no " +
            "momento. Tente novamente mais tarde."

        // Reference: https://developers.google.com/drive/api/guides/handle-errors
        private const val GD_SHARING_RATE_LIMIT_EXCEEDED = 403
        private const val GD_BACKEND_ERROR = 500

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        }

        private val SORT_LIST = listOf(
            Tag("date", "Data de criação"),
            Tag("modified", "Data de modificação"),
            Tag("title", "Título"),
            Tag("views", "Visualizações"),
        )
    }
}
