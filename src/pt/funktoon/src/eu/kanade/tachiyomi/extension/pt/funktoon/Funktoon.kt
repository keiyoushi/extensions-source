package eu.kanade.tachiyomi.extension.pt.funktoon

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Funktoon : KeiSource() {

    @Volatile
    private var token: String? = null

    override suspend fun getPopularManga(page: Int): MangasPage {
        val home = client.get("$API_URL/home", Fetch.XHR).parseAs<HomeDto>()
        return MangasPage(home.trending().map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val home = client.get("$API_URL/home", Fetch.XHR).parseAs<HomeDto>()
        return MangasPage(home.releases.map { it.toSManga() }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$API_URL/search-v2".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .addQueryParameter("type", "series")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", "20")
                .build()
            return client.get(url, Fetch.XHR).parseAs<SearchDto>().toMangasPage()
        }
        val stamp = filters.firstInstanceOrNull<CategoryFilter>()?.selected ?: return getPopularManga(page)
        return getStampPage(stamp.toString(), page)
    }

    private suspend fun getStampPage(stamp: String, page: Int): MangasPage = client.get("$API_URL/stamps/content/$stamp?page=$page", Fetch.XHR)
        .parseAs<StampPageDto>()
        .data
        .toMangasPage()

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return MangasPage(emptyList(), false)
        return when (url.pathSegments.first()) {
            "categorias" -> getStampPage(id, page)
            "vitrine" -> client.get("$API_URL/showcase/$id", Fetch.XHR).parseAs<ShowcaseDto>().toMangasPage()
            "perfil" -> client.get("$API_URL/public/author/$id?page=$page", Fetch.XHR).parseAs<AuthorPageDto>().series.toMangasPage()
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.first() != "serie") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/serie/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val serie = client.get("$API_URL${manga.url}", Fetch.XHR).use { response ->
            // An unknown series gets an empty HTML answer instead of a 404.
            if (response.body.contentType()?.subtype != "json") throw HttpException(404)
            response.parseAs<SerieDetailsDto>()
        }
        return SMangaUpdate(serie.toSManga(), serie.toChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfterLast('/')
        val cached = token
        if (cached != null) fetchPages(id, cached)?.let { return it }
        val fresh = readToken()
        if (fresh == null || fresh == cached) return emptyList()
        return fetchPages(id, fresh).orEmpty()
    }

    // Null when the reader asks for a login, so that the token is read again from the site's storage.
    private suspend fun fetchPages(id: String, token: String): List<Page>? = client.get("$API_URL/public/comic/$id", authHeaders(token), Fetch.XHR, ensureSuccess = false).use { response ->
        when (response.code) {
            401 -> null
            402 -> emptyList()
            else -> {
                if (!response.isSuccessful) throw HttpException(response.code)
                response.parseAs<ReaderDto>().toPageList()
            }
        }
    }

    // The site keeps its login in IndexedDB, out of reach of cookies and getLocalStorage.
    private suspend fun readToken(): String? = runWebView<String>(10.seconds) {
        webMessageBridge("kei", setOf(baseUrl)) { resolve(it) }
        onPageFinished { evaluateJs(READ_TOKEN) }
        loadData(baseUrl, """<link rel="icon" href="data:,">""")
    }.ifEmpty { null }.also { token = it }

    private fun authHeaders(token: String): Headers = headers.newBuilder()
        .set("Authorization", "Bearer $token")
        .set("Accept", "application/json")
        .build()

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$API_URL/get-stamps", Fetch.XHR)
        .parseAs<StampListDto>()
        .data
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<StampDto>>())

    companion object {
        private const val API_URL = "https://api.funktoon.com/api"

        private const val READ_TOKEN = """
            (() => {
              const done = (token) => kei.postMessage(token || "");
              const open = indexedDB.open("AppDatabase");
              open.onupgradeneeded = () => open.transaction.abort();
              open.onerror = () => done();
              open.onsuccess = () => {
                const db = open.result;
                if (!db.objectStoreNames.contains("AppStore")) return done();
                const get = db.transaction("AppStore").objectStore("AppStore").get(0);
                get.onsuccess = () => done(get.result?.auth?.accessToken);
                get.onerror = () => done();
              };
            })()
        """
    }
}
