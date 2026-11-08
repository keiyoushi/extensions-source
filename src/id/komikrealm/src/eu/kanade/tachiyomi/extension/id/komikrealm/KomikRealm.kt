package eu.kanade.tachiyomi.extension.id.komikrealm

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@Source
abstract class KomikRealm : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getListing("$baseUrl/popular/".toHttpUrl(), POPULAR_FUNCTION, page)

    override suspend fun getLatestUpdates(page: Int) = getListing("$baseUrl/".toHttpUrl(), LATEST_FUNCTION, page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment(query.trim())
            .addPathSegment("")
            .build()
        return getListing(url, SEARCH_FUNCTION, page)
    }

    // The first page of a list is in the list's route data, like any page of the site. The next ones come
    // from the Qwik server function of its "Load More" button, whose id only changes with the site's code.
    private suspend fun getListing(url: HttpUrl, function: String, page: Int): MangasPage {
        val series = if (page == 1) {
            getRouteData(url.toString()) { it is JsonArray && it.all { item -> item is JsonObject && "slug" in item } }.parseAs<List<Series>>()
        } else {
            loadMore(url, function, page)
        }
        // Pages hold 24 entries: a shorter one is the last, and the app shows an empty page as an error
        return MangasPage(series.map { it.toSManga() }, series.size >= PAGE_SIZE)
    }

    private suspend fun loadMore(url: HttpUrl, function: String, page: Int): List<Series> {
        val headers = headersBuilder().set("X-QRL", function).build()
        // Qwik checks the exact content type, which a String body would extend with a charset
        val body = """{"_entry":"2","_objs":["\u0002_#s_$function",$page,["0","1"]]}""".toByteArray().toRequestBody(QWIK_JSON)
        return client.post(url.newBuilder().addQueryParameter("qfunc", function).build(), headers, Fetch.XHR.from(url), body)
            .parseAs<QwikData>().decode().parseAs<List<Series>>()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2 || url.pathSegments[0] !in TYPES) return null
        val manga = SManga.create().apply { this.url = "/${url.pathSegments[0]}/${url.pathSegments[1]}/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = getRouteData(getMangaUrl(manga)) { it is JsonObject && "chapters" in it }.parseAs<Series>()
        return SMangaUpdate(series.toDetails().apply { url = manga.url }, series.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = getRouteData(getChapterUrl(chapter)) { it is JsonObject && "data" in it }
        .parseAs<ChapterRoute>().pages()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    // Data of a page's route loaders, which the site's own navigation reads from <path>/q-data.json
    private suspend fun getRouteData(pageUrl: String, predicate: (JsonElement) -> Boolean): JsonElement = client.get("${pageUrl}q-data.json", Fetch.XHR.from(pageUrl))
        .parseAs<QwikData>().decode().parseAs<RouteData>()
        .loaders.values.first(predicate)

    companion object {
        private const val POPULAR_FUNCTION = "BFnMU0fpdeM"
        private const val LATEST_FUNCTION = "aBKj8Qeh2MM"
        private const val SEARCH_FUNCTION = "HbSA6nELNQs"
        private const val PAGE_SIZE = 24
        private val QWIK_JSON = "application/qwik-json".toMediaType()
        private val TYPES = setOf("manga", "manhwa", "manhua", "doujinshi")
    }
}
