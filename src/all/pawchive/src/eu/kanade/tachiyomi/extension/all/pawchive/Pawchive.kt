package eu.kanade.tachiyomi.extension.all.pawchive

import android.os.SystemClock
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.pawchive.PawchiveCreatorDto.Companion.serviceName
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getIntOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Pawchive :
    KeiSource(),
    ConfigurableSource {
    private val preferences = getPreferences()

    private val apiPath = "api/v1"

    private val imgUrl get() = baseUrl.replace("//", "//img.")

    @Volatile
    private var fileServersUnreachable = false

    private val hostPacers = ConcurrentHashMap<String, HostPacer>()

    override fun OkHttpClient.Builder.configureClient() = apply {
        // Isolate from the app's shared Dispatcher so concurrent source downloads and DoH aren't
        // starved, and raise maxRequestsPerHost to match Mihon/Komikku's 1..15 page concurrency.
        dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 15
            },
        )
        addInterceptor(::thumbnailFallbackInterceptor)
        addInterceptor(::adaptiveRateLimitInterceptor)
        cache(
            Cache(
                directory = File(
                    applicationContext.externalCacheDir ?: applicationContext.cacheDir,
                    "network_cache_${name.lowercase()}",
                ),
                maxSize = 50L * 1024 * 1024, // 50 MiB
            ),
        )
        // Keep pooled HTTP/2 connections alive and detect dead sockets quickly during long downloads
        pingInterval(20.seconds)
    }

    // Self-tuning per-host pacer: starts unthrottled, measures the host's actual sustainable
    // interval when recovering from a 429, and slowly eases back toward full speed on successes.
    private class HostPacer {
        private var intervalMs = 0L
        private var lastDispatchMs = 0L
        private var cooldownUntilMs = 0L
        private var backingOff = false
        private var windowStartMs = 0L
        private var lastSuccessMs = 0L
        private var windowSuccesses = 0

        fun waitForTurn(chain: Interceptor.Chain) {
            while (true) {
                if (chain.call().isCanceled()) throw IOException("Canceled")
                val waitMs = synchronized(this) {
                    val now = SystemClock.elapsedRealtime()
                    val readyAt = maxOf(
                        if (intervalMs > 0L) lastDispatchMs + intervalMs else 0L,
                        cooldownUntilMs,
                    )
                    if (now >= readyAt) {
                        lastDispatchMs = now
                        0L
                    } else {
                        readyAt - now
                    }
                }
                if (waitMs <= 0L) return
                try {
                    Thread.sleep(minOf(waitMs, 250L))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Canceled", e)
                }
            }
        }

        fun onSuccess() {
            synchronized(this) {
                val now = SystemClock.elapsedRealtime()
                if (now < cooldownUntilMs) return
                if (backingOff) {
                    backingOff = false
                    if (windowSuccesses > 0) {
                        val measured = (now - windowStartMs) / windowSuccesses
                        intervalMs = maxOf(measured, intervalMs).coerceIn(100L, MAX_INTERVAL_MS)
                    }
                    windowStartMs = now
                    windowSuccesses = 1
                } else {
                    if (windowSuccesses == 0 || now - lastSuccessMs > IDLE_RESET_MS) {
                        windowStartMs = now
                        windowSuccesses = 0
                    }
                    windowSuccesses++
                    if (intervalMs > 0L) {
                        intervalMs = (intervalMs - 20L).coerceAtLeast(0L)
                    }
                }
                lastSuccessMs = now
            }
        }

        fun onRateLimited(retryAfterMs: Long) = synchronized(this) {
            val now = SystemClock.elapsedRealtime()
            val until = now + retryAfterMs
            if (!backingOff) {
                backingOff = true
                intervalMs = if (intervalMs == 0L) 500L else (intervalMs * 5 / 4).coerceAtMost(MAX_INTERVAL_MS)
            }
            cooldownUntilMs = maxOf(cooldownUntilMs, until)
        }
    }

    private fun adaptiveRateLimitInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Separate /api/ from /icons/ on the main host so cover loading never delays API calls
        val key = if (request.url.pathSegments.firstOrNull() == "api") {
            "${request.url.host}/api"
        } else {
            request.url.host
        }
        val pacer = hostPacers.computeIfAbsent(key) { HostPacer() }

        for (attempt in 0..MAX_429_RETRIES) {
            pacer.waitForTurn(chain)

            val response = chain.proceed(request)
            if (response.code == 429 && attempt < MAX_429_RETRIES) {
                val retryAfterMs = (
                    response.header("Retry-After")?.toLongOrNull()?.times(1000L)
                        ?: ((2L shl attempt) * 1000L)
                    ).coerceIn(1000L, 30_000L)
                response.close()
                pacer.onRateLimited(retryAfterMs)
                continue
            }

            if (response.isSuccessful) pacer.onSuccess()
            return response
        }

        throw IOException("Rate limit retries exhausted")
    }

    private fun thumbnailFallbackInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fallbackUrl = request.url.toThumbnailUrl()
            ?: return chain.proceed(request)

        if (!preferences.getBoolean(FALLBACK_LOW_RES_IMG, true)) {
            return chain.proceed(request)
        }

        // Skip nX file servers once unreachable for the session; tX temp_url servers are distinct
        val isFileServer = request.url.pathSegments.firstOrNull() == "data"
        var failedWithIo = false
        if (!isFileServer || !fileServersUnreachable) {
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || response.code == 429) return response
                response.close()
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                failedWithIo = true
            }
        }

        val fallbackResponse = chain.proceed(request.newBuilder().url(fallbackUrl).build())
        if (isFileServer && failedWithIo && fallbackResponse.isSuccessful) {
            fileServersUnreachable = true
        }
        return fallbackResponse
    }

    private fun HttpUrl.toThumbnailUrl(): HttpUrl? = when {
        pathSegments.firstOrNull() == "data" ->
            "$imgUrl/thumbnail$encodedPath".toHttpUrl()
        pathSegments.firstOrNull() == "f" && !fragment.isNullOrBlank() ->
            "$imgUrl/thumbnail/data$fragment".toHttpUrl()
        else -> null
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList(null))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", getLatestUpdatesFilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val isPostsMode = filters.firstInstanceOrNull<SearchModeFilter>()?.isPostsMode == true
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val typeIncluded = typeFilter?.state?.filter { it.isIncluded() }?.mapTo(HashSet()) { it.value }.orEmpty()
        val typeExcluded = typeFilter?.state?.filter { it.isExcluded() }?.mapTo(HashSet()) { it.value }.orEmpty()

        if (isPostsMode) {
            return searchPosts(page, query, typeIncluded, typeExcluded)
        }

        return searchCreators(page, query, filters, typeIncluded, typeExcluded)
    }

    private suspend fun searchCreators(
        page: Int,
        query: String,
        filters: FilterList,
        typeIncluded: Set<String>,
        typeExcluded: Set<String>,
    ): MangasPage {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val sort = sortFilter?.let {
            it.getValue() to if (it.state!!.ascending) "asc" else "desc"
        } ?: ("pop" to "desc")

        val fav = when (filters.firstInstanceOrNull<FavoritesFilter>()?.state?.firstOrNull()?.state) {
            1 -> true
            2 -> false
            else -> null
        }

        val trimmedQuery = query.trim()

        val (mangas, favoritesMap) = coroutineScope {
            val favoritesDeferred = async(Dispatchers.IO) {
                if (fav != null) {
                    val url = "$baseUrl/$apiPath/account/favorites?type=artist"
                    client.get(url, Fetch.XHR, ensureSuccess = false).use { response ->
                        if (response.code == 401) throw Exception("Log in via WebView to use Favorites")
                        if (!response.isSuccessful) throw HttpException(response.code)
                        response.parseAs<List<PawchiveFavoritesDto>>()
                            .associate { (it.service to it.id) to it.favedSeq }
                    }
                } else {
                    emptyMap()
                }
            }

            val creatorsDeferred = async(Dispatchers.IO) {
                val url = "$baseUrl/$apiPath/creators"
                val cacheControl = CacheControl.Builder().maxStale(30.minutes).build()
                client.get(url, Fetch.XHR, cacheControl).parseAs<List<PawchiveCreatorDto>>()
            }

            val favorites = favoritesDeferred.await()
            val allCreators = creatorsDeferred.await()

            val filtered = allCreators.filter { creator ->
                val sName = creator.service.serviceName().lowercase()
                val includeType = typeIncluded.isEmpty() || sName in typeIncluded
                val excludeType = typeExcluded.isNotEmpty() && sName in typeExcluded
                val matchesQuery = trimmedQuery.isEmpty() ||
                    creator.name.contains(trimmedQuery, ignoreCase = true) ||
                    creator.publicId?.contains(trimmedQuery, ignoreCase = true) == true ||
                    creator.id == trimmedQuery

                val isFavorited = when (fav) {
                    true -> (creator.service to creator.id) in favorites
                    false -> (creator.service to creator.id) !in favorites
                    null -> true
                }

                includeType && !excludeType && isFavorited && matchesQuery
            }

            filtered to favorites
        }

        val sorted = when (sort.first) {
            "pop" -> if (sort.second == "desc") mangas.sortedByDescending { it.favorited } else mangas.sortedBy { it.favorited }
            "tit" -> if (sort.second == "desc") mangas.sortedByDescending { it.name.lowercase() } else mangas.sortedBy { it.name.lowercase() }
            "serv" -> if (sort.second == "desc") mangas.sortedByDescending { it.service } else mangas.sortedBy { it.service }
            "new" -> if (sort.second == "desc") mangas.sortedByDescending { it.indexedDate } else mangas.sortedBy { it.indexedDate }
            "fav" -> {
                if (fav != true) throw Exception("Please check 'Favorites Only' Filter")
                if (sort.second == "desc") {
                    mangas.sortedByDescending { favoritesMap[it.service to it.id] ?: 0L }
                } else {
                    mangas.sortedBy { favoritesMap[it.service to it.id] ?: 0L }
                }
            }
            else -> if (sort.second == "desc") mangas.sortedByDescending { it.updatedDate } else mangas.sortedBy { it.updatedDate }
        }

        val startIndex = (page - 1) * PAGE_CREATORS_LIMIT
        val endIndex = minOf(startIndex + PAGE_CREATORS_LIMIT, sorted.size)

        if (startIndex >= sorted.size) {
            return MangasPage(emptyList(), false)
        }

        val final = sorted.subList(startIndex, endIndex).map { it.toSManga(baseUrl) }
        return MangasPage(final, endIndex < sorted.size)
    }

    private suspend fun searchPosts(
        page: Int,
        query: String,
        typeIncluded: Set<String>,
        typeExcluded: Set<String>,
    ): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("$apiPath/posts")
            .addQueryParameter("o", ((page - 1) * PAGE_POST_LIMIT).toString())
            .apply {
                val q = query.trim()
                if (q.isNotEmpty()) addQueryParameter("q", q)
            }
            .build()

        val posts: List<PawchivePostDto> = client.get(url, Fetch.XHR).parseAs()
        val datePref = preferences.getString(POST_DATE_PREF, POST_DATE_PUBLISHED)!!
        val mangas = posts
            .filter { post ->
                val sName = post.service.serviceName().lowercase()
                val includeType = typeIncluded.isEmpty() || sName in typeIncluded
                val excludeType = typeExcluded.isNotEmpty() && sName in typeExcluded
                includeType && !excludeType && post.images.isNotEmpty()
            }
            .map { it.toSManga(creator = null, baseUrl = baseUrl, imgUrl = imgUrl, datePref = datePref) }

        return MangasPage(mangas, posts.size == PAGE_POST_LIMIT)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 3 || segments[0] !in services || segments[1] != "user") return null
        val (service, _, user) = segments

        return when {
            segments.size == 3 -> fetchCreatorOrNull(service, user)?.toSManga(baseUrl)
            segments.size == 5 && segments[3] == "post" -> coroutineScope {
                val postPath = "/$service/user/$user/post/${segments[4]}"
                val creatorDeferred = async { fetchCreatorOrNull(service, user) }
                val post = fetchPostOrNull(postPath) ?: return@coroutineScope null
                val datePref = preferences.getString(POST_DATE_PREF, POST_DATE_PUBLISHED)!!
                post.toSManga(creatorDeferred.await()?.name, baseUrl, imgUrl, datePref).apply {
                    initialized = true
                }
            }
            else -> null
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val segments = getMangaUrl(manga).toHttpUrl().pathSegments.filter(String::isNotEmpty)
        if (segments.size < 3 || segments[1] != "user") return emptyList()
        val (service, _, user) = segments

        return coroutineScope {
            val linksDeferred = async { fetchLinks(service, user) }
            val creator = if (segments.size == 5 && segments[3] == "post") {
                fetchCreatorOrNull(service, user)
            } else {
                null
            }
            (listOfNotNull(creator) + linksDeferred.await())
                .distinctBy { it.service to it.id }
                .map { it.toSManga(baseUrl) }
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val segments = getMangaUrl(manga).toHttpUrl().pathSegments.filter(String::isNotEmpty)
        val (service, _, user) = segments
        val datePref = preferences.getString(POST_DATE_PREF, POST_DATE_PUBLISHED)!!

        if (segments.size == 5 && segments[3] == "post") {
            return coroutineScope {
                val creatorDeferred = if (fetchDetails) async { fetchCreatorOrNull(service, user) } else null
                val post = fetchPost(manga.url)
                val updatedManga = if (fetchDetails) {
                    post.toSManga(creatorDeferred?.await()?.name ?: manga.author, baseUrl, imgUrl, datePref)
                } else {
                    manga
                }
                val updatedChapters = if (fetchChapters) {
                    if (post.images.isNotEmpty()) {
                        listOf(
                            post.toSChapter(datePref).apply {
                                name = "Post"
                                chapter_number = 1f
                            },
                        )
                    } else {
                        emptyList()
                    }
                } else {
                    chapters
                }
                SMangaUpdate(updatedManga, updatedChapters)
            }
        }

        return coroutineScope {
            val detailsDeferred = if (fetchDetails) {
                async { fetchCreatorDetails(manga, service, user) }
            } else {
                null
            }
            val chaptersDeferred = if (fetchChapters) {
                async { fetchChapterList(manga, service, user, chapters, datePref) }
            } else {
                null
            }

            val chapterResult = chaptersDeferred?.await()
            val updatedManga = (detailsDeferred?.await() ?: manga).apply {
                if (chapterResult?.fetchedPages != null) {
                    memo = JsonObject(
                        memo + mapOf(
                            FETCHED_PAGES_MEMO to JsonPrimitive(chapterResult.fetchedPages),
                            FETCHED_DATE_MEMO to JsonPrimitive(datePref),
                        ),
                    )
                }
            }
            SMangaUpdate(updatedManga, chapterResult?.chapters ?: chapters)
        }
    }

    private suspend fun fetchCreatorDetails(manga: SManga, service: String, user: String): SManga = coroutineScope {
        val profileDeferred = async { fetchCreatorOrNull(service, user) }
        val tagsDeferred = async { fetchOptionalList<PawchiveTagDto>("$baseUrl/$apiPath/$service/user/$user/tags") }
        val announcementsDeferred = async { fetchOptionalList<PawchiveAnnouncementDto>("$baseUrl/$apiPath/$service/user/$user/announcements") }
        val linksDeferred = async { fetchLinks(service, user) }

        val creator = profileDeferred.await() ?: return@coroutineScope manga
        creator.toDetailedSManga(
            baseUrl = baseUrl,
            tags = tagsDeferred.await(),
            announcements = announcementsDeferred.await(),
            links = linksDeferred.await(),
        ).apply {
            memo = manga.memo
        }
    }

    private class ChapterListResult(
        val chapters: List<SChapter>,
        val fetchedPages: Int?,
    )

    private suspend fun fetchChapterList(
        manga: SManga,
        service: String,
        user: String,
        chapters: List<SChapter>,
        datePref: String,
    ): ChapterListResult = coroutineScope {
        val pages = preferences.getString(POST_PAGES_PREF, POST_PAGES_DEFAULT)!!
            .toInt().coerceAtMost(POST_PAGES_MAX)
        val showFancards = service == "fanbox" && preferences.getBoolean(SHOW_FANCARDS_PREF, false)
        val postChapters = chapters.filterNot { it.url.endsWith("/fancards") }
        val fetchedPages = manga.memo.getIntOrNull(FETCHED_PAGES_MEMO) ?: 0
        val fetchedDatePref = manga.memo[FETCHED_DATE_MEMO]?.stringOrNull

        // Once the configured pages are loaded for the current date preference, subsequent updates
        // only fetch posts newer than the newest known one; changing either setting reloads its pages.
        val newOnly = fetchedPages == pages && fetchedDatePref == datePref && postChapters.isNotEmpty()
        val known = postChapters.mapTo(HashSet()) { it.url }

        val fancardsDeferred = if (showFancards) async { fetchFancardsChapter(user) } else null

        var offset = 0
        var hasNextPage = true
        val newPosts = ArrayList<SChapter>()
        val maxPages = if (newOnly) POST_PAGES_MAX else pages
        var hitKnown = false

        while (hasNextPage && offset < maxPages * PAGE_POST_LIMIT) {
            val page: List<PawchivePostDto> = retry("$baseUrl/$apiPath${manga.url}/posts?o=$offset").parseAs()
            for (post in page) {
                val chapter = post.toSChapter(datePref)
                if (newOnly && chapter.url in known) {
                    hitKnown = true
                    break
                }
                if (post.images.isNotEmpty()) {
                    newPosts.add(chapter)
                }
            }
            if (hitKnown) break
            offset += PAGE_POST_LIMIT
            hasNextPage = page.size == PAGE_POST_LIMIT
        }

        val combinedPosts = if (hitKnown) {
            newPosts + postChapters
        } else {
            val loaded = newPosts.mapTo(HashSet()) { it.url }
            newPosts + postChapters.filter { it.url !in loaded }
        }

        val fancardsChapter = fancardsDeferred?.await()
        val finalChapters = if (fancardsChapter != null) {
            combinedPosts + fancardsChapter
        } else {
            combinedPosts
        }

        // Unique numbers stop "Skip duplicate chapters" merging posts
        finalChapters.forEachIndexed { index, chapter ->
            chapter.chapter_number = (finalChapters.size - index).toFloat()
        }

        ChapterListResult(finalChapters, if (!newOnly) pages else null)
    }

    private suspend fun fetchFancardsChapter(user: String): SChapter? {
        val fancards = fetchOptionalList<PawchiveFancardDto>("$baseUrl/$apiPath/fanbox/user/$user/fancards")
            .filter { it.isValidImage }
        if (fancards.isEmpty()) return null

        return SChapter.create().apply {
            url = "/fanbox/user/$user/fancards"
            name = "Fancards"
            date_upload = fancards.maxOfOrNull { it.addedDate } ?: 0L
        }
    }

    private suspend fun fetchCreatorOrNull(service: String, user: String): PawchiveCreatorDto? {
        val profileUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(apiPath)
            .addPathSegment(service)
            .addPathSegment("user")
            .addPathSegment(user)
            .addPathSegment("profile")
            .build()

        return client.get(profileUrl, Fetch.XHR, ensureSuccess = false).use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw HttpException(response.code)
            response.parseAs()
        }
    }

    private suspend fun fetchPost(path: String): PawchivePostDto = client.get("$baseUrl/$apiPath$path", Fetch.XHR).parseAs()

    private suspend fun fetchPostOrNull(path: String): PawchivePostDto? = client.get("$baseUrl/$apiPath$path", Fetch.XHR, ensureSuccess = false).use { response ->
        if (response.code == 404) return null
        if (!response.isSuccessful) throw HttpException(response.code)
        response.parseAs()
    }

    private suspend fun fetchLinks(service: String, user: String): List<PawchiveCreatorDto> = fetchOptionalList<PawchiveCreatorDto>("$baseUrl/$apiPath/$service/user/$user/links")
        .filter { it.service in services }

    private suspend inline fun <reified T> fetchOptionalList(url: String): List<T> = client.get(url, Fetch.XHR, ensureSuccess = false).use { response ->
        if (response.isSuccessful) response.parseAs() else emptyList()
    }

    private suspend fun retry(url: String): Response {
        var code = 0
        repeat(3) { attempt ->
            val response = client.get(url, Fetch.XHR, ensureSuccess = false)
            if (response.isSuccessful) return response
            code = response.code
            response.close()
            if (code !in 500..599) throw HttpException(code)
            if (attempt < 2) delay((1L shl attempt).seconds)
        }
        throw HttpException(code)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/$apiPath${chapter.url}"

        if (chapter.url.endsWith("/fancards")) {
            val fancards = client.get(url, Fetch.XHR).parseAs<List<PawchiveFancardDto>>()
                .filter { it.isValidImage }
            return fancards.mapIndexed { i, fancard ->
                Page(i, imageUrl = fancard.thumbnailUrl(imgUrl))
            }
        }

        val postData = client.get(url, Fetch.XHR).parseAs<PawchivePostDto>()
        val fallbackToThumbnail = preferences.getBoolean(FALLBACK_LOW_RES_IMG, true)
        return postData.images.mapIndexed { i, file ->
            Page(i, imageUrl = file.imageUrl(imgUrl, postData.hasFull, fallbackToThumbnail))
        }
    }

    override fun getImageRequest(page: Page): Request {
        val request = super.getImageRequest(page)
        // Page images are cached by the app; keep them out of OkHttp's cache so they don't evict /creators
        val builder = request.newBuilder().cacheControl(NO_STORE_CACHE_CONTROL)
        if (preferences.getBoolean(USE_LOW_RES_IMG, false)) {
            request.url.toThumbnailUrl()?.let { builder.url(it) }
        }
        return builder.build()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getDefaultFilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = POST_PAGES_PREF
            title = "Maximum posts to load"
            summary = "Loading more posts costs more time and network traffic.\nCurrently: %s"
            entryValues = Array(POST_PAGES_MAX) { (it + 1).toString() }
            entries = Array(POST_PAGES_MAX) { "${(it + 1)} pages (${(it + 1) * PAGE_POST_LIMIT} posts)" }
            setDefaultValue(POST_PAGES_DEFAULT)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = POST_DATE_PREF
            title = "Preferred Post Date"
            summary = "Choose which date to use for chapters.\nCurrently: %s"
            entries = arrayOf("Added Date", "Published Date", "Edited Date")
            entryValues = arrayOf(POST_DATE_ADDED, POST_DATE_PUBLISHED, POST_DATE_EDITED)
            setDefaultValue(POST_DATE_PUBLISHED)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = USE_LOW_RES_IMG
            title = "Use low resolution images"
            summary = "Reduce load time significantly by loading thumbnails instead of full-resolution images."
            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = FALLBACK_LOW_RES_IMG
            title = "Fallback to low resolution images"
            summary =
                "If a full size image fails to load, automatically try loading the low resolution thumbnail instead."
            setDefaultValue(true)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_FANCARDS_PREF
            title = "Include Fanbox Fancards"
            summary = "Add a Fancards chapter for Pixiv Fanbox creators."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private val services = setOf("patreon", "fanbox")
        private val NO_STORE_CACHE_CONTROL = CacheControl.Builder().noStore().build()

        private const val MAX_429_RETRIES = 6
        private const val MAX_INTERVAL_MS = 4_000L
        private const val IDLE_RESET_MS = 60_000L

        private const val PAGE_POST_LIMIT = 50
        private const val PAGE_CREATORS_LIMIT = 50
        const val PROMPT = "You can change how many posts to load in the extension preferences."

        private const val POST_PAGES_PREF = "POST_PAGES"
        private const val POST_PAGES_DEFAULT = "1"
        private const val POST_PAGES_MAX = 200

        private const val FETCHED_PAGES_MEMO = "fetchedPages"
        private const val FETCHED_DATE_MEMO = "fetchedDatePref"

        private const val USE_LOW_RES_IMG = "USE_LOW_RES_IMG"
        private const val FALLBACK_LOW_RES_IMG = "FALLBACK_LOW_RES_IMG"
        private const val SHOW_FANCARDS_PREF = "SHOW_FANCARDS"

        private const val POST_DATE_PREF = "POST_DATE_PREF"
        const val POST_DATE_ADDED = "added"
        const val POST_DATE_PUBLISHED = "published"
        const val POST_DATE_EDITED = "edited"
    }
}
