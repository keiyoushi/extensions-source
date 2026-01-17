package eu.kanade.tachiyomi.extension.en.kawaiscans

import eu.kanade.tachiyomi.network.GET
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

class KawaiScans : HttpSource() {
    override val name = "Kawai Scans"
    override val baseUrl = "https://kawaiscans.org"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/manga"

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val list = response.parseAs<PopularResponseDto>()
        val mangas = list.manga.map { it.toSManga(baseUrl) }
        val hasNextPage = list.currentPage < list.totalPages
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/latest", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val list = response.parseAs<List<MangaDto>>()
        val mangas = list.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val list = response.parseAs<List<MangaDto>>()
        val mangas = list.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(apiUrl + manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangas = response.parseAs<MangaDto>()
        return mangas.toSManga(baseUrl)
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangas = response.parseAs<MangaDto>()
        val mangaUrl = mangas.slug
        return mangas.chapters.map { it.toSChapter(mangaUrl) }.reversed()
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(apiUrl + chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<PageListDto>()
        return dto.pages.mapIndexed { i, url ->
            Page(i, imageUrl = baseUrl + url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
