package eu.kanade.tachiyomi.extension.en.comix

import android.content.SharedPreferences
import android.webkit.WebResourceResponse
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
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Comix :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl get() = "$baseUrl/api/v1"
    private val preferences: SharedPreferences by getPreferencesLazy()

    @Volatile
    private var cipher: ComixCipher? = null

    private val tagIdCache = object : LinkedHashMap<String, List<String>>(
        TAG_ID_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > TAG_ID_CACHE_SIZE
    }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(Descrambler.interceptor)
        .addInterceptor { chain ->
            val request = chain.request()

            val response = chain.proceed(request)
            if (response.code != 404) return@addInterceptor response

            val url = request.url.toString()
            val fallbacks = listOf("/i5/", "/si/", "/i/", "/sii/", "/ii/")
                .map { url.replaceFirst(SCRAMBLE_PATH_FALLBACK_REGEX, it) }
                .filter { it != url }

            if (fallbacks.isEmpty()) return@addInterceptor response

            var lastResponse = response
            for (fallbackUrl in fallbacks) {
                lastResponse.close()
                lastResponse = chain.proceed(request.newBuilder().url(fallbackUrl).build())
                if (lastResponse.code != 404) break
            }
            lastResponse
        }
        .rateLimit(5)

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
        getSigned<SearchResponse>("/api/v1/manga", nativeMangaParams(url))?.let { response ->
            return MangasPage(
                response.result.items.map { it.toBasicSManga(preferences.posterQuality()) },
                response.result.hasNextPage(),
            )
        }

        val document = client.get(url).asJsoup()
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
        val searchResponse = document.extractBrowseResponse() ?: runInWebView(
            document = document,
            initializationScript = """
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
            """.trimIndent(),
            buildScript = { passPayloadName, _ ->
                """
                    (function () {
                        const payloadKey = '__comixBrowsePayload';
                        const expectedKeyword = $expectedKeyword;
                        const capture = (parsed, allowEmpty = false) => {
                            try {
                                if (parsed && Array.isArray(parsed.items)) {
                                    parsed = { result: parsed };
                                }
                                if (
                                    parsed &&
                                    parsed.result &&
                                    Array.isArray(parsed.result.items) &&
                                    (allowEmpty || parsed.result.items.length > 0)
                                ) {
                                    window[payloadKey] = JSON.stringify(parsed);
                                    window.$passPayloadName(window[payloadKey]);
                                    return true;
                                }
                            } catch (e) {}
                            return false;
                        };

                        if (window[payloadKey]) return window[payloadKey];

                        try {
                            const raw = document.querySelector('script#initial-data')?.textContent;
                            const queries = raw && JSON.parse(raw).queries;
                            if (queries) Object.values(queries).some(capture);
                        } catch (e) {}

                        if (window[payloadKey]) return window[payloadKey];
                        if (window.__comixBrowseCaptureInstalled) return null;
                        window.__comixBrowseCaptureInstalled = true;

                        const captureText = text => {
                            try {
                                if (text) capture(JSON.parse(text), true);
                            } catch (e) {}
                        };

                        const shouldCaptureUrl = rawUrl => {
                            try {
                                const url = new URL(rawUrl || '', window.location.origin);
                                if (!url.pathname.includes('/api/v1/manga')) return false;
                                if (!expectedKeyword) return true;
                                return url.searchParams.get('keyword') === expectedKeyword;
                            } catch (e) {
                                return false;
                            }
                        };

                        const originalFetch = window.fetch;
                        if (typeof originalFetch === 'function') {
                            window.fetch = function () {
                                return originalFetch.apply(this, arguments).then(response => {
                                    try {
                                        const url = response && response.url || '';
                                        if (shouldCaptureUrl(url)) {
                                            response.clone().text().then(captureText).catch(() => {});
                                        }
                                    } catch (e) {}
                                    return response;
                                });
                            };
                        }

                        const originalOpen = XMLHttpRequest.prototype.open;
                        const originalSend = XMLHttpRequest.prototype.send;
                        XMLHttpRequest.prototype.open = function (method, url) {
                            this.__comixBrowseUrl = String(url || '');
                            return originalOpen.apply(this, arguments);
                        };
                        XMLHttpRequest.prototype.send = function () {
                            this.addEventListener('load', function () {
                                try {
                                    if (shouldCaptureUrl(this.__comixBrowseUrl)) {
                                        captureText(this.responseText);
                                    }
                                } catch (e) {}
                            });
                            return originalSend.apply(this, arguments);
                        };

                        const originalParse = JSON.parse;
                        const proxiedParse = new Proxy(originalParse, {
                            apply(target, thisArg, args) {
                                const parsed = Reflect.apply(target, thisArg, args);
                                if (!expectedKeyword) capture(parsed);
                                return parsed;
                            }
                        });
                        JSON.parse = proxiedParse;
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

    private fun Document.extractBrowseResponse(): SearchResponse? {
        val queries = runCatching { extractInitialQueries() }.getOrNull() ?: return null

        return queries.values.firstNotNullOfOrNull { value ->
            runCatching { value.parseAs<SearchResponse>() }
                .getOrNull()
                ?.takeIf { it.result.items.isNotEmpty() }
        }
    }

    private fun Document.extractInitialQueries(): JsonObject {
        val initialData = selectFirst("script#initial-data")?.data()
            ?: throw Exception("Could not find initial data in page")
        return initialData.parseAs<JsonObject>()["queries"] as? JsonObject
            ?: throw Exception("Could not find queries in initial data")
    }

    private fun nativeMangaParams(url: HttpUrl): Map<String, List<String>> = buildMap {
        url.queryParameterNames.forEach { name ->
            val values = url.queryParameterValues(name).filterNotNull()
            if (values.isNotEmpty()) {
                put(
                    name,
                    if (name == "content_rating") values.flatMap { it.split(',') } else values,
                )
            }
        }
        putIfAbsent("limit", listOf("28"))
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

        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("tags")
            .addPathSegment("search")
            .addQueryParameter("type", type)
            .addQueryParameter("q", name)
            .build()

        val ids = runCatching {
            client.get(url).parseAs<TagSearchResponse>().result.map { it.id.toString() }
        }.getOrNull() ?: return emptyList()
        synchronized(tagIdCache) { tagIdCache[cacheKey] = ids }
        return ids
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        var cachedDocument: Document? = null
        suspend fun getDocument(): Document {
            cachedDocument?.let { return it }
            return client.get(getMangaUrl(manga)).asJsoup().also { cachedDocument = it }
        }

        val deduplicateChapters = preferences.deduplicateChapters()
        val scanlatorBlacklist = preferences.scanlatorBlacklist()
        val blacklistSignature = scanlatorBlacklist.sorted().joinToString(",")
        val storedDeduplicateChapters = manga.memo[CHAPTER_LIST_DEDUPLICATED_MEMO]?.booleanOrNull
        val storedBlacklistSignature = manga.memo[CHAPTER_LIST_BLACKLIST_MEMO]?.string
        val fetchUntilKnown = fetchChapters &&
            preferences.fetchChaptersUntilKnown() &&
            storedDeduplicateChapters == deduplicateChapters &&
            storedBlacklistSignature == blacklistSignature
        val latestChapterId = chapters.firstOrNull()
            ?.takeIf { fetchUntilKnown }
            ?.chapterId()

        val nativeChapters = if (fetchChapters && cipher != null) {
            async { getNativeChapterList(manga, latestChapterId) }
        } else {
            null
        }

        val updatedManga = if (fetchDetails) parseMangaDetails(getDocument()) else manga
        val updatedChapters = if (fetchChapters) {
            val fetched = nativeChapters?.await()
                ?: getWebViewChapterList(manga, getDocument(), latestChapterId)
            val candidates = if (fetchUntilKnown) fetched + chapters else fetched
            selectChapters(candidates, deduplicateChapters, scanlatorBlacklist)
        } else {
            chapters
        }
        val chapterListMode = if (fetchChapters) deduplicateChapters else storedDeduplicateChapters
        val chapterListBlacklist = if (fetchChapters) {
            blacklistSignature
        } else {
            storedBlacklistSignature
        }
        if (chapterListMode != null && chapterListBlacklist != null) {
            updatedManga.memo = buildJsonObject {
                updatedManga.memo.forEach { (key, value) -> put(key, value) }
                put(CHAPTER_LIST_DEDUPLICATED_MEMO, chapterListMode)
                put(CHAPTER_LIST_BLACKLIST_MEMO, chapterListBlacklist)
            }
        }
        SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseMangaDetails(document: Document): SManga {
        val detail = document.extractInitialQueries()
            .entries.firstOrNull { (key, _) -> key.contains("\"detail\"") }
            ?.value
            ?: throw Exception("Could not find manga detail in queries")

        return detail.parseAs<Manga>().toSManga(
            preferences.posterQuality(),
            preferences.alternativeNamesInDescription(),
            preferences.scorePosition(),
            preferences.showExtraInfo(),
            preferences.showTagsInGenres(),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title${manga.url}"

    private fun SManga.mangaId(): String? = memo[MANGA_ID_MEMO]?.string
        ?: getMangaUrl(this).toHttpUrlOrNull()
            ?.pathSegments
            ?.getOrNull(1)
            ?.substringBefore('-')
            ?.takeIf { it.isNotEmpty() }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val related = document.extractInitialQueries()
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
        document: Document,
        latestChapterId: Int?,
    ): List<SChapter> {
        val mangaSlug = manga.url.removePrefix("/")
        val mangaId = manga.mangaId() ?: throw Exception("Refresh manga details")
        val webViewDocument = document.clone()
        val mainScript = webViewDocument.selectFirst(
            "script[type=module][src*=\"/dist/main-\"]",
        )
        val mainScriptUrl = mainScript?.absUrl("src").orEmpty()
        if (mainScriptUrl.isNotEmpty()) mainScript?.remove()
        val payload = runInWebView(
            document = webViewDocument,
            buildScript = { passPayloadName, rejectName ->
                $$"""
                    (function () {
                        const payloadKey = '__comixChapterPayload';
                        const mangaId = $${JSONObject.quote(mangaId)};
                        const mainScriptUrl = $${JSONObject.quote(mainScriptUrl)};
                        const latestChapterId = $${latestChapterId ?: "null"};
                        if (window[payloadKey]) return null;
                        window[payloadKey] = true;

                        (async () => {
                            try {
                                if (!mainScriptUrl) throw new Error('Could not find main bundle');
                                const mainResponse = await fetch(mainScriptUrl);
                                if (!mainResponse.ok) throw new Error('Could not load main bundle');
                                const mainJavaScript = await mainResponse.text();
                                const environmentFile = mainJavaScript.match(
                                    /from\s*["']\.\/(env-[^"']+\.js)["']/
                                )?.[1];
                                if (!environmentFile) throw new Error('Could not find environment bundle');

                                const importBundle = new Function('url', 'return import(url)');
                                const environment = await importBundle(
                                    new URL(environmentFile, mainScriptUrl).href
                                );
                                const mangaApi = Object.values(environment).find(value =>
                                    value &&
                                    typeof value === 'object' &&
                                    typeof value.chapters === 'function'
                                );
                                if (!mangaApi) throw new Error('Could not find manga API');

                                const items = [];
                                let page = 1;
                                while (page <= $${MAX_CHAPTER_PAGES}) {
                                    const response = await mangaApi.chapters(mangaId, {
                                        page,
                                        limit: 100,
                                        order: { number: 'desc' }
                                    });
                                    const pageItems = response?.items;
                                    if (!Array.isArray(pageItems) || pageItems.length === 0) break;

                                    items.push(...pageItems);
                                    if (pageItems.some(item => item.id === latestChapterId)) break;

                                    const meta = response.meta || response.pagination || {};
                                    const lastPage = meta.lastPage || meta.last_page || page;
                                    if (!(meta.hasNext || page < lastPage)) break;
                                    page++;
                                }
                                window.$${passPayloadName}(JSON.stringify(items));
                            } catch (error) {
                                window.$${rejectName}(error);
                            }
                        })();
                        return null;
                    })();
                """.trimIndent()
            },
        )

        return payload.parseAs<List<Chapter>>().map { it.toSChapter(mangaSlug) }
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

    private suspend fun getNativeChapterList(manga: SManga, latestChapterId: Int?): List<SChapter>? {
        if (cipher == null) return null
        val mangaSlug = getMangaUrl(manga).toHttpUrl().pathSegments.getOrNull(1) ?: return null
        val mangaId = manga.mangaId() ?: return null
        val chapters = mutableListOf<Chapter>()
        var page = 1
        while (page <= MAX_CHAPTER_PAGES) {
            val response = getSigned<ChapterDetailsResponse>(
                "/api/v1/manga/$mangaId/chapters",
                mapOf(
                    "limit" to listOf("100"),
                    "order[number]" to listOf("desc"),
                    "page" to listOf(page.toString()),
                ),
            ) ?: return null
            chapters += response.result.items
            val reachedKnown = response.result.items.any { it.id == latestChapterId }
            if (reachedKnown || !response.result.hasNextPage() || response.result.items.isEmpty()) break
            page++
        }
        return chapters.map { it.toSChapter(mangaSlug) }
    }

    // V3 grid-scramble pages must NOT send Origin — the server withholds X-Scramble-Seed when
    // Origin is present. Legacy byte-XOR pages need Origin to receive X-Enc-Seed.
    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl ?: return super.imageRequest(page)
        val urlWithoutFragment = imageUrl.substringBefore('#')
        val imageHost = urlWithoutFragment.toHttpUrlOrNull()?.host.orEmpty()
        val isScrambled = imageUrl.contains("#scrambled")
        val isV3 = urlWithoutFragment.toHttpUrlOrNull()?.queryParameterNames?.contains("v3") == true
        val isLegacyScramble = isScrambled && !isV3
        val baseUrlHost = baseUrl.toHttpUrl().host
        val requestHeaders = if (
            imageHost.isNotEmpty() &&
            !imageHost.endsWith(baseUrlHost) &&
            !isLegacyScramble
        ) {
            headersBuilder()
                .removeAll("Origin")
                .build()
        } else {
            headers
        }
        return GET(urlWithoutFragment, requestHeaders)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        getNativePageList(chapter)?.let { return it }

        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val payload = runInWebView(
            document = document,
            buildScript = { passPayloadName, _ ->
                """
                (function () {
                    const payloadKey = '__comixPagePayload';
                    const capture = parsed => {
                        try {
                            if (parsed && parsed.result && parsed.result.pages) {
                                window[payloadKey] = JSON.stringify(parsed);
                                window.$passPayloadName(window[payloadKey]);
                                return true;
                            }
                        } catch (e) {}
                        return false;
                    };

                    if (window[payloadKey]) return window[payloadKey];

                    try {
                        const raw = document.querySelector('script#initial-data')?.textContent;
                        const queries = raw && JSON.parse(raw).queries;
                        if (queries) Object.values(queries).some(capture);
                    } catch (e) {}

                    if (window[payloadKey]) return window[payloadKey];
                    if (JSON.parse.__comixPageCaptureInstalled) return null;
                    const originalParse = JSON.parse;
                    const proxiedParse = new Proxy(originalParse, {
                        apply(target, thisArg, args) {
                            const parsed = Reflect.apply(target, thisArg, args);
                            capture(parsed);
                            return parsed;
                        }
                    });
                    proxiedParse.__comixPageCaptureInstalled = true;
                    JSON.parse = proxiedParse;
                    return window[payloadKey] || null;
                })();
                """.trimIndent()
            },
        )

        return buildPages(payload.parseAs())
    }

    private fun buildPages(response: ChapterResponse): List<Page> {
        val pages = response.result.pages
        val base = pages.baseUrl.trimEnd('/')

        return pages.items.mapIndexed { index, img ->
            val full = if (img.url.startsWith("http")) img.url else "$base/${img.url.trimStart('/')}"
            // V3 pages need the query flag so the server returns grid-scramble headers.
            // Legacy byte-XOR pages: add #scrambled so imageRequest keeps Origin for x-enc-seed
            val isV3 = img.s == 1 || full.contains("?v3")
            val isLegacyScramble = !isV3 && (index + 1) % 4 == 0
            val url = when {
                isV3 -> full.toHttpUrl().newBuilder().apply {
                    if (!full.toHttpUrl().queryParameterNames.contains("v3")) {
                        addQueryParameter("v3", null)
                    }
                }.build().toString()
                isLegacyScramble -> "$full#scrambled"
                else -> full
            }
            Page(index, imageUrl = url)
        }
    }

    private suspend fun getNativePageList(chapter: SChapter): List<Page>? {
        if (cipher == null) return null
        val chapterId = chapter.chapterId() ?: return null
        return getSigned<ChapterResponse>("/api/v1/chapters/$chapterId", emptyMap())?.let(::buildPages)
    }

    override fun getFilterList(data: JsonElement?) = sourceFilters().getFilterList()

    private suspend inline fun <reified T> getSigned(
        path: String,
        params: Map<String, List<String>>,
    ): T? {
        val currentCipher = cipher ?: return null
        return runCatching {
            val entries = canonicalEntries(params)
            val query = entries.joinToString("&") { (name, value) ->
                "$name=${value.trim()}"
            }
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments(path.trimStart('/'))
                .apply {
                    entries.forEach { (name, value) -> addQueryParameter(name, value) }
                    addQueryParameter("_", currentCipher.sign(path, query))
                }
                .build()
            val response = client.get(url)

            val root = response.parseAs<JsonElement>()
            val decoded = if (root is JsonObject && "e" in root) {
                currentCipher.decrypt(root.parseAs<EncryptedResponse>().e).parseAs()
            } else {
                root
            }
            decoded.parseAs<T>()
        }.getOrElse {
            if (cipher === currentCipher) cipher = null
            null
        }
    }

    private fun canonicalEntries(params: Map<String, List<String>>): List<Pair<String, String>> = buildList {
        params.toSortedMap().forEach { (rawName, values) ->
            val name = rawName.removeSuffix("[]")
            if (values.size == 1 && !rawName.endsWith("[]")) {
                add(name to values.single())
            } else {
                values.forEachIndexed { index, value -> add("$name[$index]" to value) }
            }
        }
    }

    private fun encodeURIComponent(value: String): String = buildString {
        value.toByteArray().forEach { byte ->
            val char = byte.toInt() and 0xff
            if (
                char in 'A'.code..'Z'.code || char in 'a'.code..'z'.code ||
                char in '0'.code..'9'.code || char.toChar() in URI_COMPONENT_SAFE_CHARS
            ) {
                append(char.toChar())
            } else {
                append('%')
                append(HEX[char ushr 4])
                append(HEX[char and 0x0f])
            }
        }
    }

    private suspend fun runInWebView(
        document: Document,
        initializationScript: String? = null,
        buildScript: (passPayloadName: String, rejectName: String) -> String,
    ): String {
        val timeoutDeadline = AtomicLong(
            System.nanoTime() + WEBVIEW_TIMEOUT_SECONDS.seconds.inWholeNanoseconds,
        )
        val (bridgeName, errorBridgeName, passPayloadName, rejectName) = List(4) {
            (1..(10..20).random())
                .map { (('a'..'z') + ('A'..'Z')).random() }
                .joinToString("")
        }
        val result = runWebView<String>(timeout = Duration.INFINITE) {
            userAgent = headers["User-Agent"].orEmpty()
            blockImages = true

            val emptyResponse = WebResourceResponse("text/plain", "utf-8", Buffer().inputStream())
            interceptRequest { request ->
                val requestUrl = request.url?.toString()?.toHttpUrlOrNull()
                    ?: return@interceptRequest emptyResponse
                val sourceHost = baseUrl.toHttpUrl().host
                if (requestUrl.isChapterListRequest()) {
                    timeoutDeadline.set(
                        System.nanoTime() + WEBVIEW_TIMEOUT_SECONDS.seconds.inWholeNanoseconds,
                    )
                }
                val allowed = requestUrl.host == sourceHost ||
                    requestUrl.host.endsWith(".$sourceHost") ||
                    requestUrl.host == "comix.to" ||
                    requestUrl.host.endsWith(".comix.to") ||
                    requestUrl.host == "comix.ws" ||
                    requestUrl.host.endsWith(".comix.ws") ||
                    requestUrl.host == "challenges.cloudflare.com"
                if (allowed) null else emptyResponse
            }

            jsBridge(bridgeName) { resolve(it) }
            jsBridge(errorBridgeName) { reject(Exception(it)) }

            val captureScript = buildScript(passPayloadName, rejectName)
            onPageStarted { evaluateJs(captureScript) }
            onPageFinished { evaluateJs(captureScript) }
            poll(SCRIPT_RETRY_INTERVAL_MS.milliseconds) {
                if (
                    System.nanoTime() >= timeoutDeadline.get()
                ) {
                    reject(Exception("Timed out waiting for WebView"))
                } else {
                    evaluateJs(captureScript)
                }
            }

            val bootstrapScript = """
                (function () {
                    const captures = window.__comixCipherCaptures = [];
                    const originalAtob = window.atob.bind(window);
                    window.atob = function (value) {
                        const decoded = originalAtob(value);
                        try {
                            const bytes = Array.from(decoded, char => char.charCodeAt(0) & 255);
                            if (bytes.length === 256 || bytes.length === 24 || bytes.length === 32) {
                                captures.push(bytes);
                            }
                        } catch (e) {}
                        return decoded;
                    };
                    window.$passPayloadName = function (payload) {
                        const sboxes = captures.filter(item => item.length === 256).slice(0, 3);
                        const keys = captures.filter(item => item.length === 24 || item.length === 32).slice(0, 3);
                        const material = sboxes.length === 3 && keys.length === 3
                            ? { sboxes, keys }
                            : null;
                        window.$bridgeName.post(JSON.stringify({ payload, material }));
                    };
                    window.$rejectName = function (error) {
                        window.$errorBridgeName.post(String(error?.message || error));
                    };
                })();
                ${initializationScript.orEmpty()}
            """.trimIndent()
            val html = document.clone().apply {
                head().prependElement("script").append(bootstrapScript)
            }.outerHtml()
            loadData(document.location(), html)
        }.parseAs<WebViewCapture>()

        result.material?.takeIf(CipherMaterial::isValid)?.let {
            cipher = ComixCipher(it)
        }
        return result.payload
    }

    private fun HttpUrl.isChapterListRequest(): Boolean = pathSegments.size == 5 &&
        pathSegments[0] == "api" &&
        pathSegments[1] == "v1" &&
        pathSegments[2] == "manga" &&
        pathSegments[4] == "chapters"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
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
                "ratings count, and follower count to the manga description."
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

        private const val DEFAULT_CONTENT_RATING = "suggestive"
        private const val WEBVIEW_TIMEOUT_SECONDS = 120L
        private const val SCRIPT_RETRY_INTERVAL_MS = 100L
        private const val MAX_CHAPTER_PAGES = 200
        private const val OFFICIAL_GROUP_ID = 10702
        private const val HEX = "0123456789ABCDEF"
        private const val URI_COMPONENT_SAFE_CHARS = "-_.!~*'()"
        private const val TAG_ID_CACHE_SIZE = 50
        private val SCRAMBLE_PATH_FALLBACK_REGEX = Regex("/(?:i5|s?i+)/")
    }
}
