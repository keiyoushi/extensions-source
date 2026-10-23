package eu.kanade.tachiyomi.extension.en.comix

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceResponse
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.int
import keiyoushi.utils.long
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Comix :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    // A full chapter walk pages through the whole list at a paced rate, so warn once per session
    private val chapterListHintShown = AtomicBoolean(false)

    private fun showChapterListHint() {
        if (!chapterListHintShown.compareAndSet(false, true)) return
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(
                    Injekt.get<Application>(),
                    "Chapter List loading takes time for first time",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private val tagIdCache = object : LinkedHashMap<String, List<String>>(
        TAG_ID_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > TAG_ID_CACHE_SIZE
    }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(Descrambler.interceptor)
        .addInterceptor(::altHostFallback)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.fragment?.startsWith(ALT_HOSTS_FRAGMENT) == true || request.url.host == baseUrlHost) {
                return@addInterceptor chain.proceed(request)
            }

            var response = proceedWithRetry(chain, request)
            if (response.isSuccessful) return@addInterceptor response

            val url = request.url.toString()
            val fallbacks = listOf("/hi/", "/fcf/", "/i5/", "/si/", "/i/", "/sii/", "/ii/")
                .map { url.replaceFirst(SCRAMBLE_PATH_FALLBACK_REGEX, it) }
                .filter { it != url }

            if (fallbacks.isEmpty()) return@addInterceptor response

            for (fallbackUrl in fallbacks) {
                response.close()
                response = proceedWithRetry(chain, request.newBuilder().url(fallbackUrl).build())
                if (response.isSuccessful) break
            }
            response
        }
        .rateLimit(2) { it.host == baseUrlHost }
        .rateLimit(5)

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    // Comix blocks chapter-list paging that is too fast or evenly spaced
    private class RequestPacer(private val minGapMs: Long, private val maxGapMs: Long) {
        private var nextNanos = 0L

        fun waitForSlot() {
            val waitNanos = synchronized(this) {
                val now = System.nanoTime()
                val start = maxOf(now, nextNanos)
                nextNanos = start + Random.nextLong(minGapMs, maxGapMs) * 1_000_000
                start - now
            }
            if (waitNanos > 0) Thread.sleep(waitNanos / 1_000_000)
        }
    }

    // Retry failed images on alt hosts, like the site's reader
    private fun altHostFallback(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val altHosts = request.url.altHosts()
        if (altHosts.isEmpty()) return chain.proceed(request)

        val urls = listOf(request.url) + altHosts.mapIndexed { i, host ->
            request.url.newBuilder()
                .host(host)
                .setQueryParameter("r", (i + 1).toString())
                .build()
        }
        var failed: Response? = null
        var error: IOException? = null
        for (url in urls) {
            if (failed != null) {
                failed.close()
                failed = null
                runCatching { Thread.sleep(2000) }
            }
            try {
                val response = chain.proceed(request.newBuilder().url(url).build())
                if (response.isSuccessful) return response
                failed = response
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                error = e
            }
        }
        return failed ?: throw error!!
    }

    private fun HttpUrl.altHosts(): List<String> = fragment
        ?.takeIf { it.startsWith(ALT_HOSTS_FRAGMENT) }
        ?.removePrefix(ALT_HOSTS_FRAGMENT)
        ?.split(',')
        .orEmpty()

    private fun proceedWithRetry(chain: Interceptor.Chain, request: Request): Response {
        var response = chain.proceed(request)
        if (response.isSuccessful) return response

        for (attempt in 1..10) {
            if (response.code !in SERVER_ERROR_CODES && response.code != 404) break

            response.close()
            runCatching { Thread.sleep(1500) }

            val urlBuilder = request.url.newBuilder()
                .setQueryParameter("r", attempt.toString())

            val retryUrl = urlBuilder.build()
            response = chain.proceed(request.newBuilder().url(retryUrl).build())
            if (response.isSuccessful) break
        }

        return response
    }

    override fun Headers.Builder.configureHeaders() = add("Accept", "*/*")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("order[score]", "desc")
            addQueryParameter("page", page.toString())
            applyPreferenceFilters()
        }.build()
        return getMangaListFromBrowse(url)
    }

    private suspend fun getMangaListFromBrowse(url: HttpUrl): MangasPage {
        val contentRating = url.queryParameter("content_rating")
            ?: preferences.contentRating()
        val effectiveContentRating = contentRating
            .split(',')
            .lastOrNull { it.isNotBlank() }
            .orEmpty()
            .ifEmpty { "pornographic" }
        val expectedKeyword = JSONObject.quote(
            url.queryParameter("q") ?: url.queryParameter("keyword").orEmpty(),
        )
        val searchResponse = runInWebView(
            url = url.toString(),
            pageScript = { _, _, _ ->
                """
                (function () {
                    const key = 'settings_v2';
                    let settings = {};
                    try {
                        settings = JSON.parse(localStorage.getItem(key) || '{}');
                    } catch (e) {}
                    settings.state = {
                        ...(settings.state || {}),
                        contentFilter: '$effectiveContentRating'
                    };
                    if (settings.version === undefined) settings.version = 0;
                    localStorage.setItem(key, JSON.stringify(settings));
                })();
                """.trimIndent()
            },
            buildScript = { passPayloadName ->
                """
                (function () {
                    const payloadKey = '__comixA1BrowsePayload';
                    if (window[payloadKey]) return window[payloadKey];
                    if (!window.__comixA1BrowseStarted) {
                        window.__comixA1BrowseStarted = true;
                        window.__comixHarvestOptions = { timeoutMs: 60000, settleMs: 4000 };
                        ${asset("harvest_browse.js")}
                        window.__comixHarvestPromise.then(result => {
                            try {
                                const items = (result.items || []).map(item => ({
                                    hid: item.hid,
                                    title: item.title || '',
                                    url: item.url,
                                    poster: item.cover ? { small: item.cover, medium: item.cover, large: item.cover } : null,
                                }));
                                const page = (result.listParams && result.listParams.page) || 1;
                                const perPage = result.perPage || (result.listParams && result.listParams.limit) || 28;
                                const hasNext = items.length > 0 && items.length >= perPage;
                                const payload = { result: { items: items, meta: { page: page, lastPage: hasNext ? page + 1 : page, hasNext: hasNext } } };
                                window[payloadKey] = JSON.stringify(payload);
                                window.$passPayloadName(window[payloadKey]);
                            } catch (e) {}
                        }).catch(() => {});
                    }
                    return window[payloadKey] || null;
                })();
                """.trimIndent()
            },
        ).parseAs<SearchResponse>()

        val mangaList = searchResponse.result.items.map {
            it.toBasicSManga(preferences.posterQuality())
        }
        return MangasPage(mangaList, searchResponse.result.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("order[chapter_updated_at]", "desc")
            addQueryParameter("page", page.toString())
            applyPreferenceFilters()
        }.build()
        return getMangaListFromBrowse(url)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val authorIds = filters.firstInstanceOrNull<Filters.AuthorFilter>()?.state
            ?.let { resolveTagIdsForNames("author", it) }
            .orEmpty()
        val artistIds = filters.firstInstanceOrNull<Filters.ArtistFilter>()?.state
            ?.let { resolveTagIdsForNames("artist", it) }
            .orEmpty()
        val tagIds = filters.firstInstanceOrNull<Filters.TagsFilter>()?.state
            ?.let { resolveTagIdsForNames("tag", it) }
            .orEmpty()
        val hasTermSelection = filters.filterIsInstance<Filters.TermFilter>()
            .any { it.hasSelection } || tagIds.isNotEmpty()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("browse")
            .apply {
                filters.filterIsInstance<Filters.UriFilter>()
                    .filterNot { it is Filters.RequiresTermSelection && !hasTermSelection }
                    .forEach {
                        if (it is Filters.QueryAwareFilter) {
                            it.addToUri(this, query)
                        } else {
                            it.addToUri(this)
                        }
                    }

                authorIds.forEach { addQueryParameter("authors[]", it) }
                artistIds.forEach { addQueryParameter("artists[]", it) }
                tagIds.forEach { addQueryParameter("genres_in[]", it) }

                if (query.isNotBlank()) {
                    addQueryParameter("keyword", query)
                }

                addQueryParameter("page", page.toString())
            }.build()

        return getMangaListFromBrowse(url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (url.pathSegments.size < 2 || url.pathSegments[0] != "title") return null

        val mangaSlug = url.pathSegments[1]
        val mangaId = mangaSlug.substringBefore("-").takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            this.url = "/$mangaSlug"
            memo = buildJsonObject { put(MANGA_ID_MEMO, mangaId) }
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga.apply {
            this.url = manga.url
        }
    }

    private fun sourceFilters() = Filters(
        contentRating = preferences.contentRating(),
        selectedTypes = preferences.defaultTypes(),
        selectedDemographics = preferences.defaultDemographics(),
        blockedGenres = preferences.blockedGenres(),
    )

    private fun HttpUrl.Builder.applyPreferenceFilters() {
        sourceFilters().getFilterList()
            .filterIsInstance<Filters.PreferenceFilter>()
            .forEach { it.addToUri(this) }
    }

    private suspend fun resolveTagIdsForNames(type: String, raw: String): List<String> {
        val names = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return buildList {
            names.forEach { addAll(resolveTagIds(type, it)) }
        }
    }

    private suspend fun resolveTagIds(type: String, name: String): List<String> {
        val cacheKey = "$type\u0000${name.lowercase()}"
        synchronized(tagIdCache) { tagIdCache[cacheKey] }?.let { return it }

        val payload = runInWebView("$baseUrl/browse") { passPayloadName, rejectName, _ ->
            "(${asset("tags_search.js")})(${asset("site_api.js")}, " +
                "${JSONObject.quote(type)}, ${JSONObject.quote(name)}, " +
                "window.$passPayloadName, window.$rejectName);"
        }
        val ids = payload.parseAs<TagSearchResponse>().result.map { it.id.toString() }
        synchronized(tagIdCache) { tagIdCache[cacheKey] = ids }
        return ids
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var cachedQueries: JsonObject? = null
        suspend fun getQueries(): JsonObject {
            cachedQueries?.let { return it }
            return navigateForQueries(getMangaUrl(manga)).also { cachedQueries = it }
        }

        val deduplicateChapters = preferences.deduplicateChapters()
        val scanlatorBlacklist = preferences.scanlatorBlacklist()
        val blacklistSignature = scanlatorBlacklist.sorted().joinToString(",")
        // List settings live on the newest chapter; browse and search results replace a non-library entry's memo
        val listMemo = chapters.firstOrNull()?.memo?.takeIf { CHAPTER_LIST_DEDUPLICATED_MEMO in it } ?: manga.memo
        val fetchUntilKnown = fetchChapters &&
            preferences.fetchChaptersUntilKnown() &&
            listMemo[CHAPTER_LIST_DEDUPLICATED_MEMO]?.booleanOrNull == deduplicateChapters &&
            listMemo[CHAPTER_LIST_BLACKLIST_MEMO]?.string == blacklistSignature &&
            listMemo[CHAPTER_LIST_COMPLETE_MEMO]?.booleanOrNull != false
        val knownIds = if (fetchUntilKnown) chapters.mapNotNull { it.chapterId() } else emptyList()

        // Only the unchanged-list skip needs the detail before the walk; otherwise the walk's own page carries it
        val detailBeforeChapters = if (fetchUntilKnown) getQueries().mangaDetail() else null
        // Skip the chapter list when the site's last chapter upload is older than the last full read of it
        val unchanged = fetchUntilKnown && run {
            val fetchedAt = listMemo[CHAPTER_LIST_FETCHED_AT_MEMO]?.long ?: return@run false
            val uploadedBy = detailBeforeChapters?.chapterUpdatedAtFormatted?.let { parseRelativeDate(it, latest = true) } ?: 0L
            uploadedBy in 1..fetchedAt
        }
        var walkedQueries: JsonObject? = null
        val updatedChapters = if (fetchChapters && !unchanged) {
            // Site dates are relative ("2w ago"), so keep the date a chapter was first stored with
            val knownDates = chapters.mapNotNull { chapter ->
                chapter.chapterId()?.let { it to chapter.date_upload }?.takeIf { it.second > 0 }
            }.toMap()
            val mangaSlug = manga.url.removePrefix("/")
            val fetchStartedAt = System.currentTimeMillis()
            val chapterList = getWebViewChapterList(manga, knownIds)
            // The chapter page carried the detail too, so the manga comes from this same navigation
            walkedQueries = chapterList.queries?.also {
                cachedQueries = it
                rememberQueries(getMangaUrl(manga), it)
            }
            if (!chapterList.complete && chapters.isEmpty()) {
                throw Exception(chapterList.error ?: "Chapter list is incomplete")
            }
            val fetched = chapterList.chapters.map { it.toSChapter(mangaSlug) }.onEach { chapter ->
                chapter.chapterId()?.let(knownDates::get)?.let { chapter.date_upload = it }
            }
            // An incomplete list keeps the stored chapters, and the next refresh walks the whole list again
            val candidates = if (fetchUntilKnown || !chapterList.complete) fetched + chapters else fetched
            selectChapters(candidates, deduplicateChapters, scanlatorBlacklist).also { selected ->
                selected.firstOrNull()?.apply {
                    memo = buildJsonObject {
                        memo.forEach { (key, value) -> if (key != CHAPTER_LIST_FETCHED_AT_MEMO) put(key, value) }
                        put(CHAPTER_LIST_DEDUPLICATED_MEMO, deduplicateChapters)
                        put(CHAPTER_LIST_BLACKLIST_MEMO, blacklistSignature)
                        put(CHAPTER_LIST_COMPLETE_MEMO, chapterList.complete)
                        if (chapterList.complete) put(CHAPTER_LIST_FETCHED_AT_MEMO, fetchStartedAt)
                    }
                }
            }
        } else {
            chapters
        }
        val walked = walkedQueries
        val detail = when {
            detailBeforeChapters != null -> detailBeforeChapters
            !fetchDetails -> null
            walked != null -> walked.mangaDetail()
            else -> getQueries().mangaDetail()
        }
        val updatedManga = detail?.takeIf { fetchDetails }?.let(::parseMangaDetails) ?: manga
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun JsonObject.mangaDetail(): Manga = entries
        .firstOrNull { (key, _) -> key.contains("\"detail\"") }
        ?.value
        ?.parseAs<Manga>()
        ?: throw Exception("Could not find manga detail in queries")

    // The title route refuses the app's OkHttp client, so read the page's initial data from a WebView navigation
    private suspend fun navigateForQueries(url: String): JsonObject {
        val payload = runInWebView(
            url = url,
            buildScript = { passPayloadName ->
                """
                (function () {
                    const raw = document.querySelector('script#initial-data')?.textContent;
                    if (raw) window.$passPayloadName(raw);
                })();
                """.trimIndent()
            },
        )
        return (
            payload.parseAs<JsonObject>()["queries"] as? JsonObject
                ?: throw Exception("Could not find queries in initial data")
            )
            .also { rememberQueries(url, it) }
    }

    // The detail page's data, reused briefly so opening a title and its related list is one navigation
    private var lastQueries: CachedQueries? = null

    private class CachedQueries(val url: String, val atNanos: Long, val queries: JsonObject)

    private fun recentQueries(url: String): JsonObject? = synchronized(this) {
        lastQueries?.takeIf { it.url == url && System.nanoTime() - it.atNanos < RECENT_QUERIES_TTL_NANOS }?.queries
    }

    private fun rememberQueries(url: String, queries: JsonObject) = synchronized(this) {
        lastQueries = CachedQueries(url, System.nanoTime(), queries)
    }

    private fun parseMangaDetails(detail: Manga): SManga = detail.toSManga(
        preferences.posterQuality(),
        preferences.alternativeNamesInDescription(),
        preferences.scorePosition(),
        preferences.showExtraInfo(),
        preferences.showTagsInGenres(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title${manga.url}"

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = getMangaUrl(manga)
        val related = (recentQueries(url) ?: navigateForQueries(url))
            .entries.firstOrNull { (key, _) -> key.contains("\"recommended\"") }
            ?.value
            ?: return emptyList()

        return related.parseAs<SearchResponse.Items>().items.map {
            it.toBasicSManga(preferences.posterQuality())
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    private fun SChapter.chapterId(): Int? = memo[CHAPTER_ID_MEMO]?.int
        ?: getChapterUrl(this).toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull()
            ?.substringBefore("-chapter-")
            ?.toIntOrNull()

    private suspend fun getWebViewChapterList(
        manga: SManga,
        knownIds: List<Int>,
    ): ChapterList {
        if (knownIds.isEmpty()) showChapterListHint()
        val mangaId = manga.url.removePrefix("/").substringBefore('-')
        val payload = runInWebView(getMangaUrl(manga), { it.isChapterListRequest() }) { passPayloadName, _, touchName ->
            """
            (function () {
                if (window.__comixA1ChaptersStarted) return;
                window.__comixA1ChaptersStarted = true;
                window.__comixHarvestOptions = {
                    timeoutMs: 60000,
                    settleMs: 6000,
                    maxRounds: 1000,
                    stopAtIds: ${knownIds.joinToString(",", "[", "]")}
                };
                window.__comixNativeTap = function (x, y) { try { window.$touchName([x, y]); } catch (e) {} };
                ${asset("harvest_chapters.js")}
                const wrap = (result, queries) => ({
                    chapters: (result.chapters || []).map(ch => ({
                        id: ch.id,
                        url: ch.url,
                        number: ch.number,
                        name: '',
                        votes: 0,
                        createdAtFormatted: dateOf(ch.text),
                        group: ch.group ? { name: ch.group } : null,
                        isOfficial: false
                    })),
                    complete: !!result.complete,
                    error: result.error || null,
                    queries: queries
                });
                const dateOf = text => {
                    const m = /(\d+)\s*(s|m|h|d|w|mo|mos|y|yr|yrs|min|mins|sec|secs|hr|hrs|day|days|week|weeks|month|months|year|years)\s+ago/i.exec(text || '');
                    return m ? m[1] + ' ' + m[2] + ' ago' : '';
                };
                window.__comixHarvestPromise.then(result => {
                    let queries = null;
                    try {
                        const raw = document.querySelector('script#initial-data')?.textContent;
                        if (raw) queries = JSON.parse(raw).queries || null;
                    } catch (e) {}
                    window.$passPayloadName(JSON.stringify(wrap(result, queries)));
                }).catch(e => {
                    window.$passPayloadName(JSON.stringify({ chapters: [], complete: false, error: String(e), queries: null }));
                });
            })();
            """.trimIndent()
        }
        return payload.parseAs<ChapterList>()
    }

    private fun selectChapters(
        allChapters: List<SChapter>,
        shouldDeduplicate: Boolean,
        scanlatorBlacklist: Set<String>,
    ): List<SChapter> {
        val uniqueChapters = allChapters.distinctBy(SChapter::url)
        val filteredChapters = if (scanlatorBlacklist.isEmpty()) {
            uniqueChapters
        } else {
            uniqueChapters.filter { chapter ->
                chapter.scanlator.orEmpty().trim().lowercase() !in scanlatorBlacklist &&
                    chapter.groupId()?.toString() !in scanlatorBlacklist
            }
        }

        val finalChapters = if (shouldDeduplicate) {
            val chapterMap = LinkedHashMap<Float, SChapter>()
            deduplicateChapters(chapterMap, filteredChapters)
            chapterMap.values.toList()
        } else {
            filteredChapters
        }

        return finalChapters.sortedByDescending(SChapter::chapter_number)
    }

    private fun deduplicateChapters(
        chapterMap: LinkedHashMap<Float, SChapter>,
        items: List<SChapter>,
    ) {
        for (ch in items) {
            val key = ch.chapter_number
            val current = chapterMap[key]
            if (current == null) {
                chapterMap[key] = ch
            } else {
                val newIsOfficial = ch.isOfficial()
                val currentIsOfficial = current.isOfficial()
                val newIsOfficialGroup = ch.groupId() == OFFICIAL_GROUP_ID
                val currentIsOfficialGroup = current.groupId() == OFFICIAL_GROUP_ID

                val better = when {
                    newIsOfficial && !currentIsOfficial -> true
                    !newIsOfficial && currentIsOfficial -> false
                    newIsOfficialGroup && !currentIsOfficialGroup -> true
                    !newIsOfficialGroup && currentIsOfficialGroup -> false
                    else -> when {
                        ch.votes() > current.votes() -> true
                        ch.votes() < current.votes() -> false
                        else -> (ch.chapterId() ?: 0) > (current.chapterId() ?: 0)
                    }
                }
                if (better) chapterMap[key] = ch
            }
        }
    }

    private fun SChapter.votes(): Int = memo[CHAPTER_VOTES_MEMO]?.int ?: 0

    private fun SChapter.isOfficial(): Boolean = memo[CHAPTER_OFFICIAL_MEMO]?.booleanOrNull == true

    private fun SChapter.groupId(): Int? = memo[CHAPTER_GROUP_ID_MEMO]?.int

    // Comix image domains block any image requests with Referer/Origin
    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl ?: return super.imageRequest(page)
        val requestHeaders = headersBuilder()
            .removeAll("Origin")
            .removeAll("Referer")
            .build()
        return GET(imageUrl, requestHeaders)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.chapterId() ?: throw Exception("Could not find the chapter id")
        val payload = runInWebView(getChapterUrl(chapter), { it.isChapterPagesRequest() }) { passPayloadName, rejectName, _ ->
            """
            (function () {
                if (window.__comixA1PagesStarted) return;
                window.__comixA1PagesStarted = true;
                window.__comixHarvestOptions = { timeoutMs: 45000, settleMs: 4000 };
                ${asset("harvest_pages.js")}
                window.__comixHarvestPromise.then(result => {
                    const payload = {
                        baseUrl: '',
                        items: (result.images || []).map(u => ({ url: u, s: (u.indexOf('?v3') !== -1 || u.indexOf('&v3') !== -1) ? 1 : 0 }))
                    };
                    window.$passPayloadName(JSON.stringify(payload));
                }).catch(() => {
                    window.$passPayloadName(JSON.stringify({ baseUrl: '', items: [] }));
                });
            })();
            """.trimIndent()
        }

        return buildPages(payload.parseAs())
    }

    private fun buildPages(pages: ChapterPages): List<Page> {
        val base = pages.baseUrl.trimEnd('/')
        return pages.items.mapIndexed { index, img ->
            val full = (if (img.url.startsWith("http")) img.url else "$base/${img.url.trimStart('/')}").toHttpUrl()
            // V3 pages need the query flag so the server returns grid-scramble headers
            val isV3 = img.s == 1 || "v3" in full.queryParameterNames
            val url = full.newBuilder().apply {
                if (isV3 && "v3" !in full.queryParameterNames) {
                    addQueryParameter("v3", null)
                }
                val altHosts = pages.altHosts(full.host, index)
                if (altHosts.isNotEmpty()) fragment(ALT_HOSTS_FRAGMENT + altHosts.joinToString(","))
            }.build().toString()
            Page(index, imageUrl = url)
        }
    }

    override fun getFilterList(data: JsonElement?) = sourceFilters().getFilterList()

    @OptIn(NativeWebViewApi::class)
    private suspend fun runInWebView(
        url: String,
        isDataRequest: ((HttpUrl) -> Boolean)? = null,
        buildScript: ((passPayloadName: String) -> String)? = null,
        pageScript: (passPayloadName: String, rejectName: String, touchName: String) -> String = { _, _, _ -> "" },
    ): String {
        val waitSeconds = if (isDataRequest != null) DATA_REQUEST_WAIT_SECONDS else WEBVIEW_TIMEOUT_SECONDS
        val timeoutDeadline = AtomicLong(System.nanoTime() + waitSeconds.seconds.inWholeNanoseconds)
        val dataRequests = AtomicInteger()
        val (bridgeName, errorBridgeName, passPayloadName, rejectName) = List(4) { randomName() }
        val (touchBridgeName, touchName) = List(2) { randomName() }
        return runWebView(timeout = Duration.INFINITE) {
            userAgent = headers["User-Agent"].orEmpty()
            blockImages = true

            val emptyResponse = WebResourceResponse("text/plain", "utf-8", Buffer().inputStream())
            interceptRequest { request ->
                val requestUrl = request.url?.toString()?.toHttpUrlOrNull()
                    ?: return@interceptRequest emptyResponse
                if (UNUSED_SITE_PATHS.any(requestUrl.encodedPath::startsWith)) return@interceptRequest emptyResponse
                // The solver's own request for this path reads the site's page
                val isFrameLoad = request.requestHeaders["Accept"].orEmpty().contains("text/html")
                if (requestUrl.encodedPath == WAF_CHALLENGE_PATH && isFrameLoad) {
                    if (!preferences.solveCaptchas()) {
                        reject(Exception("Open in WebView to solve the captcha, or turn on \"Solve captchas\" in extension settings"))
                        return@interceptRequest emptyResponse
                    }
                    val page = "<script>(${asset("solve_captcha.js")})(parent.$rejectName)</script>"
                    return@interceptRequest WebResourceResponse("text/html", "utf-8", page.byteInputStream())
                }
                val isData = isDataRequest?.invoke(requestUrl) == true
                if (isData) dataRequests.incrementAndGet()
                if (requestUrl.isChapterListRequest()) chapterListPacer.waitForSlot()
                if (isData || requestUrl.encodedPath.startsWith(WAF_PATH)) {
                    timeoutDeadline.set(System.nanoTime() + waitSeconds.seconds.inWholeNanoseconds)
                }
                val allowed = requestUrl.host == baseUrlHost ||
                    requestUrl.host.endsWith(".$baseUrlHost") ||
                    requestUrl.host == "comix.to" ||
                    requestUrl.host.endsWith(".comix.to") ||
                    requestUrl.host == "comix.ws" ||
                    requestUrl.host.endsWith(".comix.ws") ||
                    requestUrl.host == "challenges.cloudflare.com"
                if (allowed) null else emptyResponse
            }

            jsBridge(bridgeName) { resolve(it) }
            jsBridge(errorBridgeName) { reject(Exception(it)) }
            val siteOrigin = url.toHttpUrl().run { "$scheme://$host" }
            webMessageBridge(touchBridgeName, setOf(siteOrigin)) { message ->
                val points = message.split(',').mapNotNull { it.toFloatOrNull() }
                when (points.size) {
                    2 -> tap(points[0], points[1])
                    4 -> swipe(points[0], points[1], points[2], points[3])
                    else -> throw Exception("Bad touch from the page: $message")
                }
            }
            // Refuse the site's first-tap ad window
            withNativeWebView { it.settings.setSupportMultipleWindows(true) }

            val captureScript = buildScript?.invoke(passPayloadName)
            if (captureScript != null) {
                onPageStarted { evaluateJs(captureScript) }
                onPageFinished { evaluateJs(captureScript) }
            }
            poll(SCRIPT_RETRY_INTERVAL_MS.milliseconds) {
                if (System.nanoTime() >= timeoutDeadline.get()) {
                    val message = when {
                        isDataRequest == null -> "Timed out waiting for WebView"
                        dataRequests.get() == 0 -> "Site never requested the data"
                        else -> "Nothing read from ${dataRequests.get()} site requests"
                    }
                    reject(Exception(message))
                } else if (captureScript != null) {
                    evaluateJs(captureScript)
                }
            }

            // The site's current build flags foreign inline head scripts, so inject with no element left behind
            // The bootstrap also marks the site's ad overlay as already shown (the __pu1 localStorage flag below) so it never covers the page
            val startFlagName = "window.${randomName()}"
            val bootstrapScript = """
                (() => {
                if (window.top !== window || $startFlagName) return;
                $startFlagName = true;
                try { localStorage.setItem("__pu1", "[" + Date.now() + "]"); } catch (e) {}
                window.$passPayloadName = payload => window.$bridgeName.post(payload);
                window.$rejectName = message => window.$errorBridgeName.post(message);
                window.$touchName = points => window.$touchBridgeName.postMessage(points.join(","));
                ${pageScript(passPayloadName, rejectName, touchName)}
                })();
            """.trimIndent()
            injectAtDocumentStart(bootstrapScript, setOf(siteOrigin))
            loadUrl(url)
        }
    }

    private fun randomName() = (1..(10..20).random())
        .map { (('a'..'z') + ('A'..'Z')).random() }
        .joinToString("")

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    private fun HttpUrl.isChapterPagesRequest(): Boolean = pathSegments.size == 4 &&
        pathSegments[0] == "api" &&
        pathSegments[1] == "v1" &&
        pathSegments[2] == "chapters"

    private fun HttpUrl.isChapterListRequest(): Boolean = pathSegments.size == 5 &&
        pathSegments[0] == "api" &&
        pathSegments[1] == "v1" &&
        pathSegments[2] == "manga" &&
        pathSegments[4] == "chapters"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SOLVE_CAPTCHA
            title = "Solve captchas"
            summary = "Solve the site's \"Verify you're human\" captcha automatically"
            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_FETCH_CHAPTERS_UNTIL_KNOWN
            title = "Faster chapter list fetching"
            summary = "Enabled: Uses fewer requests, but may miss newly added older chapters " +
                "(e.g. chapter 5.5 when the latest known chapter is 150).\n\n" +
                "Disabled: Finds older chapter additions, but fetching large chapter lists is slower."
            setDefaultValue(true)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_POSTER_QUALITY
            title = "Thumbnail Quality"
            summary = "Change the quality of the thumbnail. Current: %s."
            entryValues = arrayOf("small", "medium", "large")
            entries = arrayOf("Small", "Medium", "Large")
            setDefaultValue("large")
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_CONTENT_RATING
            title = "Content rating"
            summary = "Maximum content rating shown in popular, latest, and search " +
                "results. The Content rating filter in search overrides this. " +
                "Current: %s."
            entries = arrayOf("Show all", "Safe only", "Up to Suggestive", "Up to Erotica", "Up to Pornographic")
            entryValues = arrayOf("", "safe", "suggestive", "erotica", "pornographic")
            setDefaultValue(DEFAULT_CONTENT_RATING)
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_TYPES
            title = "Default types"
            summary = "Types to include in popular, latest, and search results. " +
                "The Type filter in search overrides this."
            entries = Filters.getTypes().map { it.first }.toTypedArray()
            entryValues = Filters.getTypes().map { it.second }.toTypedArray()
            setDefaultValue(Filters.getTypes().map { it.second }.toSet())
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_DEMOGRAPHICS
            title = "Default demographics"
            summary = "Demographics to include in popular, latest, and search " +
                "results. The Demographic filter in search overrides this."
            entries = Filters.getDemographics().map { it.first }.toTypedArray()
            entryValues = Filters.getDemographics().map { it.second }.toTypedArray()
            setDefaultValue(Filters.getDemographics().map { it.second }.toSet())
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = PREF_BLOCKED_GENRES
            title = "Blocked genres"
            summary = "Genres always excluded from results. The search filter " +
                "can still include a blocked genre as a one-off override."
            entries = Filters.getGenres().map { it.first }.toTypedArray()
            entryValues = Filters.getGenres().map { it.second }.toTypedArray()
            setDefaultValue(emptySet<String>())
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = DEDUPLICATE_CHAPTERS
            title = "Deduplicate Chapters"
            summary = "Remove duplicate chapters from the chapter list.\n" +
                "Official chapters (Comix-marked) are preferred, followed by the highest-voted or most recent.\n" +
                "Warning: It can be slow on large lists."
            setDefaultValue(false)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_SCANLATOR_BLACKLIST
            title = "Scanlator Blacklist"
            summary = "Filter out chapters from specific groups. Comma-separated list of group names or group IDs (e.g., 'Violet Scans, 307')."
            dialogTitle = "Exclude groups"
            setDefaultValue("")
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = ALTERNATIVE_NAMES_IN_DESCRIPTION
            title = "Show Alternative Names in Description"
            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_EXTRA_INFO
            title = "Show extra info in description"
            summary = "Append publication year, language, content rating, rank, " +
                "ratings count, follower count, and tracker links to the manga description."
            setDefaultValue(true)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_TAGS_IN_GENRES
            title = "Show tags in genre chips"
            summary = "Include the site's narrative tag list (e.g. Demons, " +
                "Vampires, Time Travel) alongside the curated genres in the " +
                "manga details. Off by default — the curated set matches what " +
                "the site itself shows on the page."
            setDefaultValue(false)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_SCORE_POSITION
            title = "Score display position"
            summary = "%s"
            entries = arrayOf("Top of description", "Bottom of description", "Don't show")
            entryValues = arrayOf("top", "bottom", "none")
            setDefaultValue("top")
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.posterQuality() = getString(PREF_POSTER_QUALITY, "large")

    private fun SharedPreferences.deduplicateChapters() = getBoolean(DEDUPLICATE_CHAPTERS, false)

    private fun SharedPreferences.fetchChaptersUntilKnown() = getBoolean(PREF_FETCH_CHAPTERS_UNTIL_KNOWN, true)

    private fun SharedPreferences.scanlatorBlacklist(): Set<String> = getString(PREF_SCANLATOR_BLACKLIST, "")
        ?.split(",")
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotEmpty() }
        ?.toSet() ?: emptySet()

    private fun SharedPreferences.solveCaptchas() = getBoolean(PREF_SOLVE_CAPTCHA, false)

    private fun SharedPreferences.alternativeNamesInDescription() = getBoolean(ALTERNATIVE_NAMES_IN_DESCRIPTION, false)

    private fun SharedPreferences.scorePosition() = getString(PREF_SCORE_POSITION, "top") ?: "top"

    private fun SharedPreferences.showExtraInfo() = getBoolean(PREF_SHOW_EXTRA_INFO, true)

    private fun SharedPreferences.showTagsInGenres() = getBoolean(PREF_SHOW_TAGS_IN_GENRES, false)

    private fun SharedPreferences.defaultTypes(): Set<String> {
        val all = Filters.getTypes().map { it.second }.toSet()
        return getStringSet(PREF_DEFAULT_TYPES, all) ?: all
    }

    private fun SharedPreferences.defaultDemographics(): Set<String> {
        val all = Filters.getDemographics().map { it.second }.toSet()
        return getStringSet(PREF_DEFAULT_DEMOGRAPHICS, all) ?: all
    }

    private fun SharedPreferences.blockedGenres(): Set<String> = getStringSet(PREF_BLOCKED_GENRES, emptySet()) ?: emptySet()

    // The legacy "Hide NSFW" boolean still exists in some users' preferences;
    // map it to a sensible default until they pick a value explicitly.
    private fun SharedPreferences.contentRating(): String {
        if (contains(PREF_CONTENT_RATING)) {
            return getString(PREF_CONTENT_RATING, DEFAULT_CONTENT_RATING) ?: DEFAULT_CONTENT_RATING
        }
        if (contains(LEGACY_HIDE_NSFW_PREF) && !getBoolean(LEGACY_HIDE_NSFW_PREF, true)) {
            return ""
        }
        return DEFAULT_CONTENT_RATING
    }

    companion object {
        private const val PREF_POSTER_QUALITY = "pref_poster_quality"
        private const val PREF_CONTENT_RATING = "pref_content_rating"
        private const val PREF_DEFAULT_TYPES = "pref_default_types"
        private const val PREF_DEFAULT_DEMOGRAPHICS = "pref_default_demographics"
        private const val PREF_BLOCKED_GENRES = "pref_blocked_genres"
        private const val LEGACY_HIDE_NSFW_PREF = "nsfw_pref"
        private const val DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
        private const val PREF_FETCH_CHAPTERS_UNTIL_KNOWN = "pref_fetch_chapters_until_known"
        private const val PREF_SCANLATOR_BLACKLIST = "pref_scanlator_blacklist"
        private const val ALTERNATIVE_NAMES_IN_DESCRIPTION = "pref_alt_names_in_description"
        private const val PREF_SHOW_EXTRA_INFO = "pref_show_extra_info"
        private const val PREF_SHOW_TAGS_IN_GENRES = "pref_show_tags_in_genres"
        private const val PREF_SCORE_POSITION = "pref_score_position"
        private const val PREF_SOLVE_CAPTCHA = "pref_solve_captcha"

        private const val DEFAULT_CONTENT_RATING = "suggestive"
        private const val WEBVIEW_TIMEOUT_SECONDS = 120L
        private const val DATA_REQUEST_WAIT_SECONDS = 30L
        private val RECENT_QUERIES_TTL_NANOS = 120.seconds.inWholeNanoseconds
        private const val SCRIPT_RETRY_INTERVAL_MS = 100L
        private const val MAX_CHAPTER_PAGES = 1000
        private val chapterListPacer = RequestPacer(minGapMs = 2500, maxGapMs = 5000)
        private const val OFFICIAL_GROUP_ID = 10702
        private const val TAG_ID_CACHE_SIZE = 50
        private val SCRAMBLE_PATH_FALLBACK_REGEX = Regex("/(?:i5|s?i+)/")
        private val SERVER_ERROR_CODES = setOf(502, 503, 522, 523)
        private const val ALT_HOSTS_FRAGMENT = "alt="

        // Site requests in the WebView that the extension has no use for: error reports, comments, upload images
        private val UNUSED_SITE_PATHS = listOf("/api/v1/client-errors", "/api/v1/threads/", "/assets/uploads/")

        // Site's captcha page, shown when the API answers 403 captcha_required
        private const val WAF_CHALLENGE_PATH = "/@waf/challenge"

        // Site's captcha endpoints, which also keep a WebView run alive while the solver works
        private const val WAF_PATH = "/@waf/"
    }
}
