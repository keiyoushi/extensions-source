package eu.kanade.tachiyomi.extension.all.cinblue

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Source
abstract class CinBlue : KeiSource() {

    override suspend fun getPopularManga(page: Int) = search(page, "", "popular-today")

    override suspend fun getLatestUpdates(page: Int) = search(page, "", "date")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = search(page, query, filters.firstInstance<SortFilter>().value)

    // The API reads tags in the query (language:"english") and takes "-" for an empty one
    private suspend fun search(page: Int, query: String, sort: String): MangasPage {
        val terms = listOfNotNull(query.trim().ifEmpty { null }, LANGUAGES[lang]?.let { "language:\"$it\"" })
            .joinToString(" ")
            .ifEmpty { "-" }
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment(terms)
            .addPathSegment(page.toString())
            .addPathSegment(sort)
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<SearchDto>()
        return MangasPage(result.result.map { it.toSManga() }, page < result.numPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.takeIf { it.size >= 2 && it[0] in setOf("v", "g") }?.get(1)?.toIntOrNull() ?: return null
        val book = getBook(id.toString())
        val language = LANGUAGES[lang]
        if (language != null && !book.isIn(language)) return null
        return book.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val book = getBook(manga.url)
        return SMangaUpdate(book.toSManga(), listOf(book.toSChapter()))
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/v/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/v/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = getBook(chapter.url).toPages()

    // The site loads its pages with XHRs, and the image server refuses them without their Origin
    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .fetch(Fetch.XHR)
        .build()

    override fun getFilterList(data: JsonElement?) = getFilters()

    private suspend fun getBook(id: String) = client.get("$API_URL/book/$id", Fetch.XHR).parseAs<BookDto>()

    companion object {
        private const val API_URL = "https://same.yui.pw/api/v6"

        private val LANGUAGES = mapOf("en" to "english", "ja" to "japanese", "zh" to "chinese")
    }
}
