package eu.kanade.tachiyomi.extension.pt.roxinha

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Roxinha : HttpSource() {

    override val name = "Roxinha"
    override val baseUrl = "https://roxinha.online"
    override val lang = "pt-BR"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 24
        return GET("$apiUrl/manga/search/advanced?sort=views&order=DESC&limit=24&offset=$offset", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()
        val (mangas, hasMore) = dto.toMangasPage(baseUrl)
        return MangasPage(mangas, hasMore)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * 24
        return GET("$apiUrl/manga/search/advanced?sort=updatedAt&order=DESC&limit=24&offset=$offset", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val offset = (page - 1) * 24
        val url = "$apiUrl/manga/search/advanced".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "24")
            addQueryParameter("offset", offset.toString())
            addQueryParameter("mode", "default")

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            if (sortFilter != null) {
                val sortFields = arrayOf("title", "updatedAt", "views", "avgRating")
                val sortIndex = sortFilter.state?.index ?: 2
                addQueryParameter("sort", sortFields[sortIndex])
                addQueryParameter("order", if (sortFilter.state?.ascending == true) "ASC" else "DESC")
            } else {
                addQueryParameter("sort", "title")
                addQueryParameter("order", "ASC")
            }

            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
            if (statusFilter != null && statusFilter.state != 0) {
                val statusFields = arrayOf("", "ongoing", "completed")
                addQueryParameter("status", statusFields[statusFilter.state])
            }

            val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
            if (typeFilter != null && typeFilter.state != 0) {
                val typeFields = arrayOf("", "Manga", "Manhua", "Manhwa", "Webtoon")
                addQueryParameter("type", typeFields[typeFilter.state])
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDto>()
        return dto.toSManga(baseUrl)
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<MangaDto>()
        return dto.toSChapters()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/chapter/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/manga/chapter/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterDetailsDto>()
        return dto.toPages(baseUrl)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
    )
}
