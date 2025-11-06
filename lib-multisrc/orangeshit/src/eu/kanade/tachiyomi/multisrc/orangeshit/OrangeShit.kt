package eu.kanade.tachiyomi.multisrc.orangeshit

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

abstract class OrangeShit(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    val scanId: Long = 1,
) : HttpSource() {

    override val supportsLatest = true

    protected open val apiUrl = "https://api.mediocretoons.com"

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageLocation)
        .build()

    open val targetAudience: TargetAudience = TargetAudience.All

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", scanId.toString())
        .set("x-app-key", "toons-mediocre-app")

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl/obras?ordenarPor=views_hoje&limite=20", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaDto>>>()
        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false
        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/novos".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameterIf(targetAudience != TargetAudience.All, "formato", targetAudience.toString())
            .build()
        return GET(url, headers)
    }

    private fun HttpUrl.Builder.addQueryParameterIf(predicate: Boolean, name: String, value: String): HttpUrl.Builder {
        if (predicate) addQueryParameter(name, value)
        return this
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaDto>>>()
        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false
        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("obr_nome", query)
            .addQueryParameter("limite", "8")
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("todos_generos", "true")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaDto>>>()
        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false
        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val pathSegment = manga.url.replace("/obra/", "/obras/")
        return GET("$apiUrl$pathSegment", headers)
    }

    override fun mangaDetailsParse(response: Response) =
        response.parseAs<MediocreMangaDto>().toSManga()

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<MediocreMangaDto>().capitulos.map { it.toSChapter() }
            .distinctBy(SChapter::url)

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/capitulos/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> =
        response.parseAs<MediocreChapterDetailDto>().toPageList()

    override fun imageUrlParse(response: Response): String = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================= Interceptors =================================

    private fun imageLocation(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.isSuccessful) {
            return response
        }

        response.close()

        val url = request.url.newBuilder()
            .dropPathSegment(4)
            .build()

        val newRequest = request.newBuilder()
            .url(url)
            .build()
        return chain.proceed(newRequest)
    }

    // ============================= Utilities ====================================

    private fun HttpUrl.Builder.dropPathSegment(count: Int): HttpUrl.Builder {
        repeat(count) {
            removePathSegment(0)
        }
        return this
    }

    enum class TargetAudience(val value: Int) {
        All(1),
        Shoujo(4),
        Yaoi(7),
        ;

        override fun toString() = value.toString()
    }

    companion object {
        const val CDN_URL = "https://cdn.mediocretoons.com"
    }
}
