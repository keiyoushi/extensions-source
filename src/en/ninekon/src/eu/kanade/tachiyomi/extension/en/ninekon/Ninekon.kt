package eu.kanade.tachiyomi.extension.en.ninekon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Ninekon : KeiSource() {

    private val apiUrl = "https://api.ninekon.com/1.0"

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchBooks("$apiUrl/books?sort=views&page=$page".toHttpUrl(), page)

    private suspend fun fetchBooks(url: HttpUrl, page: Int): MangasPage {
        val data = client.get(url).parseAs<BooksResponse>()
        return MangasPage(data.books.map { it.toSManga() }, page < data.pages)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchBooks("$apiUrl/books?sort=dt&order=desc&page=$page".toHttpUrl(), page)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return fetchBooks(url.build(), page)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get("$apiUrl/books/${manga.url}").parseAs<BookDetailsDto>()
        return SMangaUpdate(data.toSManga(), data.getChapters())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/book/${manga.url}"

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.replace("/books/", "/book/").replace("/chapters/", "/chapter/")

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(apiUrl + chapter.url).parseAs<PagesDto>()
        return data.getImages().mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        GenreFilter(),
    )
}
