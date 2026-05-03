package eu.kanade.tachiyomi.extension.fr.mangamoins

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.Locale

class MangaMoins : HttpSource() {

    override val name = "MangaMoins"

    override val baseUrl = "https://mangamoins.com"

    override val lang = "fr"

    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/v1"

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (response.code == 403 && request.url.toString().contains("/api/v1/")) {
                response.close()
                val homeRequest = GET(baseUrl, headers)
                super.client.newCall(homeRequest).execute().close()
                return@addInterceptor chain.proceed(request)
            }
            response
        }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/trend", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<TrendResponse>()
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, false) // Trend API has no pagination
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", MANGA_PAGE_LIMIT.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.page * result.limit < result.total
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/explore".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", MANGA_PAGE_LIMIT.toString())
            .apply {
                if (query.isNotEmpty()) {
                    addQueryParameter("q", query)
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("manga", manga.url.toMangaSlug())
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailsResponse>()
        val info = result.info
        return SManga.create().apply {
            title = info.title.unescapeHtml()
            author = info.author.unescapeHtml()
            artist = info.author.unescapeHtml()
            description = info.description.unescapeHtml().ifBlank { null }
            status = when {
                info.status.lowercase(Locale.FRENCH).contains("en cours") -> SManga.ONGOING
                info.status.lowercase(Locale.FRENCH).contains("termin") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = info.cover
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url.toMangaSlug()}"

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailsResponse>()
        return result.chapters.map { ch ->
            SChapter.create().apply {
                name = buildString {
                    val chapterName = "Chapitre ${ch.num.toString().removeSuffix(".0")}"
                    append(chapterName)
                    val title = ch.title.unescapeHtml()
                    if (title.isNotBlank() && !title.equals(chapterName, ignoreCase = true)) {
                        append(" - ")
                        append(title)
                    }
                }
                url = ch.slug
                date_upload = ch.time * 1000L
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterSlug = chapter.url.removePrefix("/scan/")
        return "$baseUrl/scan/$chapterSlug"
    }

    // ============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterSlug = chapter.url.removePrefix("/scan/")
        val url = "$apiUrl/scan".toHttpUrl().newBuilder()
            .addQueryParameter("slug", chapterSlug)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ScanResponse>()
        val baseUrl = data.pagesBaseUrl.removeSuffix("/")
        return (1..data.pageNumbers).map { i ->
            val pageNum = i.toString().padStart(2, '0')
            Page(i - 1, imageUrl = "$baseUrl/$pageNum.webp")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val MANGA_PAGE_LIMIT = 20
    }
}
