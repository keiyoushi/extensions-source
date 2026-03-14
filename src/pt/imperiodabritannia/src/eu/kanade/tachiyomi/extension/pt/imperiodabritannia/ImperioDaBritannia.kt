package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class ImperioDaBritannia : HttpSource() {
    override val baseUrl: String = "https://imperiodabritannia.net"

    private val apiUrl: String = "$baseUrl/api/obras"

    override val name: String = "Sagrado Império da Britannia"

    override val lang: String = "pt-BR"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .addInterceptor(AESDecryptInterceptor())
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl?pagina=$page&limite=24", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<Pageable>()
        val mangas = dto.content.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, hasNextPage = dto.hasNextPage())
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/recentes?pagina=$page&limite=20", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("busca", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .apply {
                if (slug.toLongOrNull() == null) {
                    addPathSegment("by-slug")
                }
                addPathSegment(slug)
            }
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDetailsDto>().toSManga(baseUrl)

    // Chapters

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<MangaDetailsDto>().toSChapterList()

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.url.split("/").filter(String::isNotBlank)
        return GET("$apiUrl/${segments[segments.size - 3]}/capitulos/${segments.last()}", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PageDto>().toPageList()

    override fun imageUrlParse(response: Response): String = ""
}
