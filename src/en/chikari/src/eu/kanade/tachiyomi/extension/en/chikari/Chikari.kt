package eu.kanade.tachiyomi.extension.en.chikari

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Chikari : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder {
        rateLimit(permits = 1, period = 2.seconds) { it.host == baseUrl.toHttpUrl().host }
        return addInterceptor(::verificationInterceptor)
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", getFilterList(null))

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val filters = getFilterList(null).apply { firstInstance<SortFilter>().state = SORTS.indexOfFirst { it.value == "updated" } }
        return getSearchMangaList(page, "", filters)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().selected
        val url = apiUrl.newBuilder().apply {
            addPathSegment("series")
            addQueryParameter("sort", sort.value)
            sort.period?.let { addQueryParameter("period", it) }
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            // Like the site, no type at all means its default types
            filters.firstInstance<TypeFilter>().checked.ifEmpty { TypeFilter().checked }.forEach { addQueryParameter("type", it) }
            filters.firstInstance<StatusFilter>().checked.forEach { addQueryParameter("status", it) }
            filters.firstInstance<GenreFilter>().included.forEach { addQueryParameter("genre", it) }
            filters.firstInstance<GenreFilter>().excluded.forEach { addQueryParameter("genre_exclude", it) }
            filters.firstInstance<ContentRatingFilter>().checked.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("content_rating", it.joinToString(",")) }
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
        }.build()
        val result = client.get(url, Fetch.XHR).parseAs<SeriesListDto>()
        return MangasPage(result.items.map { it.toSManga() }, page * PAGE_SIZE < result.total)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/series/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) getChapters(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getDetails(manga: SManga): SManga {
        val url = apiUrl.newBuilder().addPathSegments(manga.url.removePrefix("/")).build()
        return client.get(url, Fetch.XHR.from(getMangaUrl(manga))).parseAs<SeriesDetailsDto>().toSManga()
    }

    private suspend fun getChapters(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<ChapterDto>()
        do {
            val url = apiUrl.newBuilder().apply {
                addPathSegments(manga.url.removePrefix("/"))
                addPathSegment("chapters")
                addQueryParameter("order", "desc")
                addQueryParameter("limit", CHAPTERS_PAGE_SIZE.toString())
                addQueryParameter("offset", chapters.size.toString())
            }.build()
            val result = client.get(url, Fetch.XHR.from(getMangaUrl(manga))).parseAs<ChapterListDto>()
            chapters += result.items
        } while (result.items.isNotEmpty() && chapters.size < result.total)
        return chapters.flatMap { it.toSChapters(manga.url) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val (_, slug, number) = chapterUrl.pathSegments
        val url = apiUrl.newBuilder().apply {
            addPathSegment("series")
            addPathSegment(slug)
            addPathSegment("chapters")
            addPathSegment(number)
            chapterUrl.queryParameter("scan")?.let { addQueryParameter("scan", it) }
        }.build()
        val pages = client.get(url, Fetch.XHR.from(chapterUrl.toString())).parseAs<PagesDto>().pages
        return pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        ContentRatingFilter(),
        GenreFilter(),
    )

    private val apiUrl get() = "$baseUrl/api".toHttpUrl()

    // The API asks for a Turnstile check after too many requests, which the site shows in its pages
    private fun verificationInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 429 && response.peekBody(1024).string().contains("challenge_required")) {
            response.close()
            throw IOException("Too many requests: open the WebView and complete the site's verification")
        }
        return response
    }

    companion object {
        private const val PAGE_SIZE = 36
        private const val CHAPTERS_PAGE_SIZE = 200
    }
}
