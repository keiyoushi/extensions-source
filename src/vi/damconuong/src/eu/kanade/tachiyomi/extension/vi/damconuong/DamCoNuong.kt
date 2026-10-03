package eu.kanade.tachiyomi.extension.vi.damconuong

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient

@Source
abstract class DamCoNuong : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(5)
        addInterceptor(ScrambleInterceptor())
        addInterceptor(authInterceptor())
    }

    private val preferences by getPreferencesLazy()

    private val authMutex = Mutex()

    @Volatile private var authToken: String? = null

    @Volatile private var apiHost: String? = null

    private fun authInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val token = authToken?.takeIf { it.isNotBlank() }
        if (token != null && request.url.host == apiHost) {
            chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        } else {
            chain.proceed(request)
        }
    }

    private suspend fun loadAuthToken() {
        if (!authToken.isNullOrBlank()) return
        authMutex.withLock {
            if (!authToken.isNullOrBlank()) return@withLock
            authToken = readAuthTokenFromWebView()
        }
    }

    private suspend fun readAuthTokenFromWebView(): String? {
        val raw = getLocalStorage(baseUrl, "auth-storage") ?: return null
        return raw.parseAs<AuthStorage>().state?.token?.takeIf { it.isNotBlank() }
    }

    private suspend fun refreshAuthToken() {
        authMutex.withLock {
            authToken = readAuthTokenFromWebView()
        }
    }

    private fun isLoginRequired(text: String): Boolean = text.contains("\"code\":\"login_required\"") || text.contains("Login required to read")

    private suspend fun api(): String = ApiBase.get(client, baseUrl, preferences).also {
        apiHost = it.toHttpUrl().host
    }

    private suspend fun fetchJson(url: String): String {
        loadAuthToken()
        var text = client.get(url, ensureSuccess = false).use { it.body.string() }
        if (isLoginRequired(text)) {
            refreshAuthToken()
            if (authToken.isNullOrBlank()) {
                throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
            }
            text = client.get(url, ensureSuccess = false).use { it.body.string() }
            if (isLoginRequired(text)) {
                throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
            }
        }
        return text
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(page, query = "", filters = FilterList(SortFilter().apply { state = 3 }))

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaList(page, query = "", filters = FilterList(SortFilter().apply { state = 0 }))

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = fetchMangaList(page, query, filters)

    private suspend fun fetchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "-updated_at"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty()
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "name"
        val minRating = filters.firstInstanceOrNull<MinRatingFilter>()?.toUriPart().orEmpty()
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()

        val acceptGenres = genres
            .filter { it.state == Filter.TriState.STATE_INCLUDE }
            .joinToString(",") { it.id.toString() }
        val rejectGenres = genres
            .filter { it.state == Filter.TriState.STATE_EXCLUDE }
            .joinToString(",") { it.id.toString() }

        val url = "${api()}/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "24")
            .addQueryParameter("sort", sort)
            .addQueryParameter("include", "genres,artist,latest_chapter")
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("filter[$searchType]", query)
                }
                if (status.isNotEmpty()) {
                    addQueryParameter("filter[status]", status)
                }
                if (acceptGenres.isNotEmpty()) {
                    addQueryParameter("filter[accept_genres]", acceptGenres)
                }
                if (rejectGenres.isNotEmpty()) {
                    addQueryParameter("filter[reject_genres]", rejectGenres)
                }
                if (minRating.isNotEmpty()) {
                    addQueryParameter("filter[min_rating]", minRating)
                }
            }
            .build()

        return client.get(url).parseAs<ListResponse>().toMangasPage()
    }

    private fun ListResponse.toMangasPage(): MangasPage {
        val pagination = meta?.pagination
        val hasNextPage = pagination != null && pagination.currentPage < pagination.lastPage
        return MangasPage(data.map { it.toSManga() }, hasNextPage)
    }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return fetchJson("${api()}/mangas/$slug?include=artist,author,group,genres")
            .parseAs<DetailResponse>()
            .data
            .toSMangaDetails()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trimStart('/').substringAfterLast('/')

        return coroutineScope {
            val detailsDeferred = async {
                if (!fetchDetails) return@async manga
                val dto = fetchJson("${api()}/mangas/$slug?include=artist,author,group,genres")
                    .parseAs<DetailResponse>()
                    .data
                dto.toSMangaDetails().apply {
                    this.url = manga.url
                    memo = buildJsonObject {
                        dto.group?.slug?.let { put("group_slug", it) }
                        dto.author?.slug?.let { put("author_slug", it) }
                        dto.artist?.slug?.let { put("artist_slug", it) }
                        dto.genres.firstOrNull()?.slug?.let { put("genre_slug", it) }
                    }
                }
            }
            val chaptersDeferred = async {
                if (fetchChapters) fetchChapterList(slug) else chapters
            }

            SMangaUpdate(
                manga = detailsDeferred.await(),
                chapters = chaptersDeferred.await(),
            )
        }
    }

    private suspend fun fetchChapterList(mangaSlug: String): List<SChapter> {
        val result = mutableListOf<SChapter>()
        var page = 1
        var lastPage = 1

        do {
            val response = fetchJson(
                "${api()}/mangas/$mangaSlug/chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("per_page", "2000")
                    .addQueryParameter("sort", "desc")
                    .build()
                    .toString(),
            ).parseAs<ChapterListResponse>()

            result += response.data.map { it.toSChapter(mangaSlug) }
            lastPage = response.meta?.pagination?.lastPage ?: 1
            page++
        } while (page <= lastPage)

        return result
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val slug = manga.url.trimStart('/').substringAfterLast('/')
        val sources = listOfNotNull(
            manga.memo["group_slug"]?.stringOrNull?.let { "groups" to it },
            manga.memo["author_slug"]?.stringOrNull?.let { "authors" to it },
            manga.memo["artist_slug"]?.stringOrNull?.let { "artists" to it },
            manga.memo["genre_slug"]?.stringOrNull?.let { "genres" to it },
        ).ifEmpty {
            val detail = fetchJson("${api()}/mangas/$slug?include=artist,author,group,genres")
                .parseAs<DetailResponse>()
                .data
            listOfNotNull(
                detail.group?.slug?.let { "groups" to it },
                detail.author?.slug?.let { "authors" to it },
                detail.artist?.slug?.let { "artists" to it },
                detail.genres.firstOrNull()?.slug?.let { "genres" to it },
            )
        }

        for ((type, taxonomySlug) in sources) {
            val list = client.get("${api()}/$type/$taxonomySlug/mangas?per_page=12")
                .parseAs<ListResponse>()
                .data
            val related = list.filter { it.slug != slug }.map { it.toSManga() }
            if (related.isNotEmpty()) return related.take(12)
        }
        return emptyList()
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.trimStart('/').split('/')
        val mangaSlug = segments.getOrNull(1)
        val chapterSlug = segments.getOrNull(2)
        if (mangaSlug.isNullOrEmpty() || chapterSlug.isNullOrEmpty()) {
            throw Exception("Invalid chapter url: ${chapter.url}")
        }

        val path = "$mangaSlug/$chapterSlug"
        PagesCrypto.ensureLoaded(client, baseUrl, preferences)
        val token = PagesCrypto.token(mangaSlug, chapterSlug)
        val response = fetchJson("${api()}/mangas/$mangaSlug/chapters/$chapterSlug/pages?_=$token")
            .parseAs<PagesResponse>()

        val payload = PagesCrypto.decryptPages(response.encrypted, token, path)
        return payload.pages.mapIndexedNotNull { index, src ->
            if (src.isBlank()) return@mapIndexedNotNull null
            val key = payload.scrambleKeys?.getOrNull(index)?.takeIf { it.isNotEmpty() }
            val imageUrl = if (key != null) "$src#$key" else src
            Page(index, url = imageUrl, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = mutableListOf<GenreOption>()
        var page = 1
        var lastPage = 1

        do {
            val response = client.get("${api()}/genres?per_page=100&page=$page")
                .parseAs<GenreListResponse>()
            genres += response.data
            lastPage = response.meta?.pagination?.lastPage ?: 1
            page++
        } while (page <= lastPage)

        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())
}
