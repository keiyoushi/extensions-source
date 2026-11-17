package eu.kanade.tachiyomi.extension.ar.mangaswat

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
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaSwat : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1) { it.pathSegments.getOrNull(1) == "api" }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("series/", page) {
        addQueryParameter("order_by", "-followers_count")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("series/releases/", page) {}

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList("series/", page) {
        addQueryParameter("search", query)
        filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val mangaId = when (url.pathSegments.first()) {
            "series" -> id
            "chapter" -> getChapter(id.toString()).serie.id
            else -> return null
        }
        return getMangaDetails(mangaId.toString()).apply { initialized = true }
    }

    private suspend fun getMangaList(path: String, page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = api(path).apply(params)
            .addQueryParameter("page_size", "100")
            .addQueryParameter("page", page.toString())
            .build()
        val data = client.get(url, Fetch.XHR).parseAs<MangaListDto>()
        return MangasPage(data.results.map { it.toSManga() }, data.next != null)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async {
            if (fetchDetails) getMangaDetails(manga.url) else manga
        }

        val chaptersDeferred = async {
            if (fetchChapters) getChapterList(manga) else chapters
        }

        SMangaUpdate(
            manga = mangaDeferred.await(),
            chapters = chaptersDeferred.await(),
        )
    }

    private suspend fun getMangaDetails(id: String): SManga = client.get(api("series/$id/").build(), Fetch.XHR)
        .parseAs<MangaDetailsDto>()
        .toSManga()

    private suspend fun getChapterList(manga: SManga): List<SChapter> = buildList {
        var page = 1
        do {
            val url = api("chapters/").apply {
                addQueryParameter("serie", manga.url)
                addQueryParameter("order_by", "-order")
                addQueryParameter("page_size", "200")
                addQueryParameter("page", (page++).toString())
            }.build()
            val data = client.get(url, Fetch.XHR).parseAs<ChapterListDto>()
            addAll(data.results.map { it.toSChapter() })
        } while (data.next != null)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.chapterId}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = getChapter(chapter.chapterId).images
        .sortedBy { it.order }
        .mapIndexed { i, page -> Page(i, imageUrl = page.image.toAppHost()) }

    private suspend fun getChapter(id: String): PageListDto = client.get(api("chapters/$id/").build(), Fetch.XHR).parseAs()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(api("genres/").build(), Fetch.XHR).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            data?.parseAs<List<GenreDto>>()?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
        ),
    )

    private fun api(path: String): HttpUrl.Builder = API_URL.newBuilder().addPathSegments(path)

    private val SChapter.chapterId get() = url.removePrefix("/chapters/").substringBefore("/")

    companion object {
        // The site's mobile app host; the website's images redirect to a banner
        internal const val APP_HOST = "appswat.com"
        private val API_URL = "https://$APP_HOST/v2/api/v2/".toHttpUrl()
    }
}
