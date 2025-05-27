package eu.kanade.tachiyomi.extension.pt.gekkouscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
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
import okhttp3.internal.http.HTTP_FORBIDDEN
import java.io.IOException

class GekkouScans : HttpSource() {

    override val name: String = "Gekkou Scans"

    override val baseUrl: String = "https://new.gekkou.space"

    private val apiUrl = "$baseUrl/api"

    override val lang: String = "pt-BR"

    override val supportsLatest: Boolean = true

    // Moved from Madara
    override val versionId: Int = 2

    override val client = super.client.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 2, 1)
        .addInterceptor(::verifyLogin)
        .build()

    // ========================= Popular ====================================

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/manga/todos", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<MangaDto>>()
            .sortedByDescending(MangaDto::popular)
            .map(MangaDto::toSManga)
        return MangasPage(mangas, false)
    }

    // ========================= Latest =====================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiUrl/manga/recent-updates", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<LatestMangaDto>>().map {
            SManga.create().apply {
                title = it.name
                url = "/projeto/${it.slug}"
                thumbnail_url = it.thumbnailUrl
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Search =====================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga/search".toHttpUrl().newBuilder()
            .setQueryParameter("query", query)
            .setQueryParameter("limit", "10")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Details ====================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/manga/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<MangaDto>().let(MangaDto::toSManga)

    // ========================= Chapters ===================================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<MangaDto>().toListSChapter().sortedByDescending(SChapter::chapter_number)

    // ========================= Pages ======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val pathSegment = chapter.url.split("/").filter(String::isNotBlank)
            .drop(1).joinToString("/")
        return addRequestRequireSettings("$apiUrl/chapter/$pathSegment".toHttpUrl())
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<PagesDto>().pages.sortedBy(PagesDto.ImageUrl::index).map {
            val imageUrl = "$apiUrl${it.url}"
            Page(it.index, imageUrl = imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request {
        return addRequestRequireSettings(super.imageRequest(page).url)
    }

    override fun imageUrlParse(response: Response): String = ""

    // ========================= Utilities ======================================

    private fun addRequestRequireSettings(url: HttpUrl): Request {
        val newUrl = url.newBuilder()
            .addQueryParameter("cb", unixTime().toString())
            .build()

        // It's possible to add a real user here.
        val newHeaders = headers.newBuilder()
            .set("User-Id", (1..5000).random().toString())
            .build()

        return GET(newUrl, newHeaders)
    }

    private fun verifyLogin(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request()).takeIf { it.code != HTTP_FORBIDDEN } ?: throw IOException("Faça o login na WebView")

    private fun unixTime(): Int {
        val timestampMillis = System.currentTimeMillis()
        return (timestampMillis / 1000).toInt()
    }
}
