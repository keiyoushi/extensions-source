package eu.kanade.tachiyomi.extension.pt.azuretoons

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
import okhttp3.Request
import okhttp3.Response

class Azuretoons : HttpSource() {

    override val name = "Azuretoons"
    override val baseUrl = "https://azuretoons.com"
    override val lang = "pt-BR"
    override val supportsLatest = false

    private val apiUrl = "https://azuretoons.com/api"

    private val rateLimitPerSecond = 2

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(rateLimitPerSecond)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    // ============================== Popular (Browse) =======================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<List<AzuretoonsMangaDto>>()
        val mangas = dto.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = false)
    }

    // =============================== Search (na mão: filtra por título) =====
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.queryParameter("search")?.lowercase()
        val dto = response.parseAs<List<AzuretoonsMangaDto>>()
        val mangas = dto
            .map { it.toSManga() }
            .let { list ->
                if (!query.isNullOrEmpty()) {
                    list.filter { it.title.lowercase().contains(query) }
                } else {
                    list
                }
            }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================ Manga Details ============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/obra/").substringBefore("/")
        return GET("$apiUrl/obras/slug/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<AzuretoonsMangaDto>()
        return dto.toSManga(isDetails = true)
    }

    // ============================== Chapters ================================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<AzuretoonsMangaDto>()
        return manga.chapters
            .map { it.toSChapter(manga.slug ?: "") }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    // =============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/capitulo/")
        val slug = chapter.url.substringAfter("/obra/").substringBefore("/")
        return GET("$apiUrl/chapters/read/$slug/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<AzuretoonsChapterDetailDto>()
        return dto.toPageList()
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
}
