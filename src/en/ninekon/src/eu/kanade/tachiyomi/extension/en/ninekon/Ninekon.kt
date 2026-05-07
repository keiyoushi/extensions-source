package eu.kanade.tachiyomi.extension.en.ninekon

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

class Ninekon : HttpSource() {

    override val name = "Ninekon"

    override val baseUrl = "https://app.ninekon.com"
    private val apiUrl = "https://api.ninekon.com/1.0"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/books?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<BooksResponse>()
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return MangasPage(data.books.map { it.toSManga() }, page < data.pages)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/books?sort=dt&order=desc&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/books".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("field", "title")
            url.addQueryParameter("query", query)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.map { it.value }

        if (!genres.isNullOrEmpty()) {
            url.addQueryParameter("tags", genres.joinToString(","))
        }

        if (sortFilter != null) {
            val sorts = arrayOf("dt", "title", "rates", "views")
            val state = sortFilter.state
            if (state != null) {
                url.addQueryParameter("sort", sorts[state.index])
                url.addQueryParameter("order", if (state.ascending) "asc" else "desc")
            }
        } else {
            url.addQueryParameter("sort", "dt")
            url.addQueryParameter("order", "desc")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/books/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<BookDetailsDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/book/${manga.url}"

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<BookDetailsDto>()
        return data.getChapters()
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.replace("/books/", "/book/").replace("/chapters/", "/chapter/")

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET(apiUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PagesDto>()
        return data.getImages().mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        GenreFilter(),
    )
}
