package eu.kanade.tachiyomi.extension.pt.taosect

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TaoSect : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    override fun Headers.Builder.configureHeaders() = set("User-Agent", USER_AGENT)

    private val apiHeaders: Headers
        get() = headers.newBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

    private var latestIds: List<String> = emptyList()

    private var latestChapterPage = 1

    override suspend fun getPopularManga(page: Int): MangasPage {
        val apiUrl = "$baseUrl/$API_BASE_PATH/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderby", "views")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PROJECTS_PER_PAGE.toString())
            .addQueryParameter("_fields", DEFAULT_FIELDS)
            .build()

        return projectListParse(client.get(apiUrl, apiHeaders), page)
    }

    private fun projectListParse(response: Response, page: Int): MangasPage {
        val lastPage = response.headers["X-Wp-TotalPages"]!!.toInt()
        val result = response.parseAs<List<TaoSectProjectDto>>()

        val projectList = result.map(::popularMangaFromObject)

        return MangasPage(projectList, page < lastPage)
    }

    private fun popularMangaFromObject(obj: TaoSectProjectDto): SManga = SManga.create().apply {
        title = Parser.unescapeEntities(obj.title!!.rendered, true)
        thumbnail_url = obj.thumbnail
        setUrlWithoutDomain(obj.link!!)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) {
            latestIds = emptyList()
            latestChapterPage = 1
        }

        // Chapters of one project can fill whole pages; an empty page would stop paging in the app
        var projectIds: List<String>
        var hasNextPage: Boolean
        do {
            val apiUrl = "$baseUrl/$API_BASE_PATH/capitulos".toHttpUrl().newBuilder()
                .addQueryParameter("order", "desc")
                .addQueryParameter("orderby", "date")
                .addQueryParameter("page", latestChapterPage.toString())
                .addQueryParameter("per_page", LATEST_CHAPTERS_PER_PAGE.toString())
                .addQueryParameter("_fields", "post_id")
                .build()

            val response = client.get(apiUrl, apiHeaders)
            val lastPage = response.headers["X-Wp-TotalPages"]!!.toInt()
            val result = response.parseAs<List<TaoSectChapterDto>>()

            hasNextPage = latestChapterPage++ < lastPage
            projectIds = result
                .map { it.projectId!! }
                .distinct()
                .filterNot { latestIds.contains(it) }
        } while (projectIds.isEmpty() && hasNextPage)

        latestIds = latestIds + projectIds

        if (projectIds.isEmpty()) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val projectsApiUrl = "$baseUrl/$API_BASE_PATH/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("include", projectIds.joinToString(","))
            .addQueryParameter("per_page", projectIds.size.toString())
            .addQueryParameter("orderby", "include")
            .addQueryParameter("_fields", DEFAULT_FIELDS)
            .build()
        val projectsResult = client.get(projectsApiUrl, apiHeaders).parseAs<List<TaoSectProjectDto>>()

        val projectList = projectsResult.map(::popularMangaFromObject)

        return MangasPage(projectList, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        val projectSlug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank)
            ?: return null

        return client.get(projectApiUrl(projectSlug, DEFAULT_FIELDS), apiHeaders)
            .parseAs<List<TaoSectProjectDto>>()
            .firstOrNull()
            ?.let(::popularMangaFromObject)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val apiUrl = "$baseUrl/$API_BASE_PATH/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PROJECTS_PER_PAGE.toString())
            .addQueryParameter("_fields", DEFAULT_FIELDS)

        if (query.isNotEmpty()) {
            apiUrl.addQueryParameter("search", query)
        }

        filters.filterIsInstance<QueryParameterFilter>()
            .forEach { it.toQueryParameter(apiUrl, query) }

        return projectListParse(client.get(apiUrl.build(), apiHeaders), page)
    }

    private fun projectApiUrl(projectSlug: String, fields: String): HttpUrl = "$baseUrl/$API_BASE_PATH/projetos".toHttpUrl().newBuilder()
        .addQueryParameter("per_page", "1")
        .addQueryParameter("slug", projectSlug)
        .addQueryParameter("_fields", fields)
        .build()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val projectSlug = manga.url
            .substringAfterLast("projeto/")
            .substringBefore("/")

        val details = if (fetchDetails) async { fetchMangaDetails(manga, projectSlug) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(projectSlug) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private suspend fun fetchMangaDetails(manga: SManga, projectSlug: String): SManga {
        val apiUrl = projectApiUrl(projectSlug, "title,informacoes,content,thumbnail,link")
        val result = client.get(apiUrl, apiHeaders).parseAs<List<TaoSectProjectDto>>()

        if (result.isEmpty()) {
            throw Exception(PROJECT_NOT_FOUND)
        }

        val project = result[0]

        return manga.apply {
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

    private suspend fun fetchChapterList(projectSlug: String): List<SChapter> {
        val apiUrl = "$baseUrl/$API_BASE_PATH/capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("projeto", projectSlug)
            .addQueryParameter("per_page", "1000")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderby", "sequencia")
            .addQueryParameter("_fields", "nome_capitulo,post_id,slug,data_insercao")
            .build()

        val result = client.get(apiUrl, apiHeaders).parseAs<List<TaoSectChapterDto>>()

        if (result.isEmpty()) {
            throw Exception(CHAPTERS_NOT_FOUND)
        }

        // Count the project views, requested by the scanlator.
        countProjectView(result[0].projectId!!)

        return result.map { chapterFromObject(it, projectSlug) }
    }

    private fun chapterFromObject(obj: TaoSectChapterDto, projectSlug: String): SChapter = SChapter.create().apply {
        name = obj.name
        scanlator = this@TaoSect.name
        date_upload = DATE_FORMATTER.tryParseDateTime(obj.date)
        url = "/leitor-online/projeto/$projectSlug/${obj.slug}/"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
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
            .build()

        val result = client.get(apiUrl, apiHeaders).parseAs<TaoSectChapterDto>()

        if (result.pages.isEmpty()) {
            return emptyList()
        }

        val chapterUrl = "$baseUrl/leitor-online/projeto/$projectSlug/$chapterSlug"

        val pages = result.pages.mapIndexed { i, pageUrl ->
            Page(i, chapterUrl, pageUrl)
        }

        // Count the project and chapter views, requested by the scanlator.
        countProjectView(result.projectId!!, result.id)

        // Check if the pages have exceeded the view limit of Google Drive.
        val firstPage = pages[0]

        val hasExceededViewLimit = runCatching {
            val firstPageRequest = imageRequest(firstPage)

            client.get(firstPageRequest.url, firstPageRequest.headers, ensureSuccess = false).use {
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

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Accept", ACCEPT_IMAGE)
        .header("Referer", page.url)
        .build()

    private suspend fun countProjectView(projectId: String, chapterId: String? = null) {
        val formBodyBuilder = FormBody.Builder()
            .add("action", "update_views_v2")
            .add("projeto", projectId)

        if (chapterId != null) {
            formBodyBuilder.add("capitulo", chapterId)
        }

        runCatching {
            client.post("$baseUrl/wp-admin/admin-ajax.php", formBodyBuilder.build()).close()
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        CountryFilter(getCountryList()),
        StatusFilter(getStatusList()),
        GenreFilter(getGenreList()),
        SortFilter(SORT_LIST, DEFAULT_ORDERBY),
        FeaturedFilter(),
        NsfwFilter(),
    )

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

    private class GoogleDriveResponse(val isValid: Boolean, val code: Int) {
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

        private const val API_BASE_PATH = "wp-json/wp/v2"
        private const val PROJECTS_PER_PAGE = 18
        private const val LATEST_CHAPTERS_PER_PAGE = 100
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

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

        private val SORT_LIST = listOf(
            Tag("date", "Data de criação"),
            Tag("modified", "Data de modificação"),
            Tag("title", "Título"),
            Tag("views", "Visualizações"),
        )
    }
}
