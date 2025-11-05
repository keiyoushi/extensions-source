package eu.kanade.tachiyomi.extension.en.atsumaru

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

class Atsumaru : HttpSource() {

    override val versionId = 2

    override val name = "Atsumaru"

    override val baseUrl = "https://atsu.moe"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "*/*")
        add("Host", "atsu.moe")
    }

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/infinite/trending?page=${page - 1}&types=Manga,Manwha,Manhua,OEL", apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<BrowseMangaDto>().items

        return MangasPage(data.map { it.toSManga(baseUrl) }, true)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/infinite/recentlyUpdated?page=${page - 1}&types=Manga,Manwha,Manhua,OEL", apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/collections/manga/documents/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("query_by", "title,englishTitle,otherNames")
            .addQueryParameter("limit", "24")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("query_by_weights", "3,2,1")
            .addQueryParameter("include_fields", "id,title,englishTitle,poster")
            .addQueryParameter("num_typos", "4,3,2")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResultsDto>()

        return MangasPage(data.hits.map { it.document.toSManga(baseUrl) }, data.hasNextPage())
    }

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/manga/${manga.url}"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/manga/page?id=${manga.url}", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaObjectDto>().mangaPage.toSManga(baseUrl)
    }

    // ============================== Chapters ==============================

    private fun fetchChaptersRequest(mangaId: String, page: Int): Request {
        return GET("$baseUrl/api/manga/chapters?id=$mangaId&filter=all&sort=desc&page=$page", apiHeaders)
    }

    override fun chapterListRequest(manga: SManga): Request {
        return fetchChaptersRequest(manga.url, 0)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.queryParameter("id")!!
        val chapterList = mutableListOf<ChapterDto>()

        var result = response.parseAs<ChapterListDto>()
        chapterList.addAll(result.chapters)

        while (result.hasNextPage()) {
            result = client.newCall(fetchChaptersRequest(mangaId, result.page + 1)).execute().parseAs()
            chapterList.addAll(result.chapters)
        }

        return chapterList.map { it.toSChapter(mangaId) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, name) = chapter.url.split("/")
        return "$baseUrl/read/$slug/$name"
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val (slug, name) = chapter.url.split("/")
        val url = "$baseUrl/api/read/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("mangaId", slug)
            .addQueryParameter("chapterId", name)

        return GET(url.build(), apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<PageObjectDto>().readChapter.pages.mapIndexed { index, page ->
            Page(index, imageUrl = baseUrl + page.image)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}
