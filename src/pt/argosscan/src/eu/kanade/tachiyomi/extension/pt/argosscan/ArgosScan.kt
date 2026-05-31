package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ArgosScan : HttpSource() {

    override val name = "Argos Scan"

    override val baseUrl = "https://argoscomics.online"
    private val apiUrl = "https://api.argoscomics.online"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()

        if (request.url.host.startsWith("api.")) {
            val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            val hasAuth = cookies.any { it.name == "argos_auth_token" && it.value.isNotEmpty() }

            if (!hasAuth) {
                throw IOException("Login necessário. Abra o WebView e faça login com o Discord para usar a extensão.")
            }
        }

        val response = chain.proceed(request)

        if (response.code == 401 || response.code == 403) {
            throw IOException("Sessão expirada. Faça login novamente no WebView.")
        }

        response
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(authInterceptor)
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/projects", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<ProjectResponseDto>().toSMangaList()
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$apiUrl/projects#${query.trim()}", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment ?: ""
        val mangas = response.parseAs<ProjectResponseDto>().toSMangaList(query)
        return MangasPage(mangas, false)
    }

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/projects/slug/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<ProjectDto>().toSManga()

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val slug = manga.url.substringAfterLast("/")

        // 1. Fetch details first to extract the project ID
        val detailsReq = GET("$apiUrl/projects/slug/$slug", headers)
        val detailsRes = client.newCall(detailsReq).execute()

        if (!detailsRes.isSuccessful) {
            throw IOException("Falha ao buscar os detalhes do projeto.")
        }
        val projectDto = detailsRes.parseAs<ProjectDto>()

        // 2. Fetch the chapters using the required project_id
        val chaptersReq = GET("$apiUrl/chapters?kind=published&project_id=${projectDto.id}", headers)
        val chaptersRes = client.newCall(chaptersReq).execute()

        if (!chaptersRes.isSuccessful) {
            throw IOException("Falha ao buscar os capítulos.")
        }

        chaptersRes.parseAs<ChapterResponseDto>().toSChapterList(projectDto.id, dateFormat)
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used.")

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.split("|")
        val chapterId = parts[0]
        val projectId = parts[1]

        // Passing chapter_id as URL fragment prevents it from being sent over the network
        return GET("$apiUrl/chapters?kind=published&project_id=$projectId#$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.fragment ?: throw Exception("ID do capítulo não encontrado.")
        return response.parseAs<ChapterResponseDto>().getImagesForChapter(chapterId)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")
}
