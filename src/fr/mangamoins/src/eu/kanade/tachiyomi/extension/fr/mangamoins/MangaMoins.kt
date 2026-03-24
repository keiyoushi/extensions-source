package eu.kanade.tachiyomi.extension.fr.mangamoins

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.util.Locale

class MangaMoins : HttpSource() {

    override val name = "MangaMoins"

    override val baseUrl = "https://mangamoins.com"

    override val lang = "fr"

    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/v1"

    private val coverMap: Map<String, String> by lazy {
        val mangaListUrl = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("limit", "999")
            .build()
        client.newCall(GET(mangaListUrl, headers)).execute().use { resp ->
            resp.parseAs<MangaListResponse>().data.associate {
                it.title to it.coverFolder
            }
        }
    }
    private val seenLatestUrls = mutableSetOf<String>()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", MANGA_PAGE_LIMIT.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.data.map { it.toSManga(baseUrl) }
        val hasNextPage = result.page * result.limit < result.total
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ================================

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        if (page == 1) {
            seenLatestUrls.clear()
        }
        return super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * LATEST_PAGE_LIMIT
        val url = "$apiUrl/latest-chapters".toHttpUrl().newBuilder()
            .addQueryParameter("offset", offset.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestChaptersResponse>()
        val mangas = result.items
            .filter { seenLatestUrls.add(it.title) }
            .map { it.toSManga(baseUrl, coverMap) }
        val hasNextPage = result.items.size >= LATEST_PAGE_LIMIT
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", MANGA_PAGE_LIMIT.toString())
            .addQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("manga", manga.url)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailsResponse>()
        val info = result.info
        return SManga.create().apply {
            title = info.title
            author = info.author
            artist = info.author
            description = info.description.ifBlank { null }
            status = if (info.status.lowercase(Locale.FRENCH) == "en cours") {
                SManga.ONGOING
            } else {
                SManga.UNKNOWN
            }
            thumbnail_url = info.cover.let { cover ->
                if (cover.startsWith("http")) {
                    cover
                } else {
                    "$baseUrl/${cover.removePrefix("../../")}"
                }
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailsResponse>()
        return result.chapters.map { ch ->
            SChapter.create().apply {
                name = "${ch.num} - ${ch.title}"
                url = "/scan/${ch.folder}"
                date_upload = ch.time * 1000L
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("link[rel=preload][as=image]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("href"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val MANGA_PAGE_LIMIT = 20
        private const val LATEST_PAGE_LIMIT = 20
    }
}
