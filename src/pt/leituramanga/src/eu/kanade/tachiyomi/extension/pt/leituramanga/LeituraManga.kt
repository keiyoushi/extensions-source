package eu.kanade.tachiyomi.extension.pt.leituramanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class LeituraManga : HttpSource() {

    override val name = "Leitura Mangá"

    override val baseUrl = "https://leituramanga.net"

    private val apiUrl = "https://api.leituramanga.net"

    private val cdnUrl = "https://cdn.leituramanga.net"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ================= Popular ==================
    override fun popularMangaRequest(page: Int) = GET("$apiUrl/api/manga/get-tops?limit=200", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaResponseDto<PopularDataDto>>()
        val mangas = result.data.topView.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ================= Latest ==================

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/api/manga/?sort=time&limit=24&isHome=true&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<MangaResponseDto<MangaListDto>>()
        val mangas = result.data.data.map { it.toSManga(cdnUrl) }
        val hasNext = result.data.pagination.let { it.page < it.totalPage }
        return MangasPage(mangas, hasNext)
    }
    // ================= Search ==================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "24")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.selectedValue.isNotEmpty()) {
                        url.addQueryParameter("genre", filter.selectedValue)
                    }
                }

                is StatusFilter -> {
                    if (filter.selectedValue.isNotEmpty()) {
                        url.addQueryParameter("status", filter.selectedValue)
                    }
                }

                is SortFilter -> {
                    url.addQueryParameter("sort", filter.selectedValue)
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun getFilterList() = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )
    // ================= Details ==================

    override fun mangaDetailsRequest(manga: SManga) = GET("$apiUrl/api/manga/slug/${manga.url.removeSuffix("/").substringAfterLast("/")}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaResponseDto<MangaDto>>()
        return result.data.toSManga(cdnUrl)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url
    // ================= Chapters ==================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaDto = response.parseAs<MangaResponseDto<MangaDto>>().data
        val request = GET("$apiUrl/api/chapter/get-by-manga-id?mangaId=${mangaDto._id}&page=1&limit=9999", headers)
        val result = client.newCall(request).execute().parseAs<MangaResponseDto<ChapterListDto>>()

        return result.data.data.map { it.toSChapter(mangaDto.slug) }
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url
    // ================= Pages ==================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("div[data-index] img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
