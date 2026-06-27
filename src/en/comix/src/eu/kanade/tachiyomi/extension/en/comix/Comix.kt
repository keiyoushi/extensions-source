package eu.kanade.tachiyomi.extension.en.comix

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import org.jsoup.nodes.Document
import rx.Observable
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class Comix :
    HttpSource(),
    ConfigurableSource {

    override val name = "Comix"
    override val baseUrl = "https://comix.to"
    private val apiUrl = "https://comix.to/api/v1"
    override val lang = "en"
    override val supportsLatest = true
    override val supportsRelatedMangas = false
    override val disableRelatedMangasBySearch = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(Descrambler.interceptor)
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
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "*/*")

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // V3 grid-scramble pages must NOT send Origin — the server withholds X-Scramble-Seed when
    // Origin is present. Legacy byte-XOR pages need Origin to receive X-Enc-Seed.
    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl ?: return super.imageRequest(page)
        val urlWithoutFragment = imageUrl.substringBefore('#')
        val imageHost = urlWithoutFragment.toHttpUrlOrNull()?.host.orEmpty()
        val isScrambled = imageUrl.contains("#scrambled")
        val isV3 = urlWithoutFragment.toHttpUrlOrNull()?.queryParameterNames?.contains("v3") == true
        val isLegacyScramble = isScrambled && !isV3
        val requestHeaders = if (imageHost.isNotEmpty() && !imageHost.contains("comix.to") && !isLegacyScramble) {
            headersBuilder()
                .removeAll("Origin")
                .build()
        } else {
            headers
        }
        return GET(urlWithoutFragment, requestHeaders)
    }

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("order[score]", "desc")
            addQueryParameter("page", page.toString())
            applyBrowseContentPreferences()
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = fetchMangaListFromBrowse(
        popularMangaRequest(page),
    )

    private fun fetchMangaListFromBrowse(request: Request): Observable<MangasPage> = Observable.fromCallable {
        val document = runBlocking {
            client.newCall(request).awaitSuccess().asJsoup()
        }
        val contentRating = request.url.queryParameter("content_rating")
            ?: preferences.contentRating()
        val effectiveContentRating = contentRating
            .split(',')
            .lastOrNull { it.isNotBlank() }
            .orEmpty()
            .ifEmpty { "pornographic" }
        val expectedKeyword = JSONObject.quote(
            request.url.queryParameter("q") ?: request.url.queryParameter("keyword").orEmpty(),
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
            buildScript = { interfaceName ->
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
                                    window.$interfaceName.passPayload(window[payloadKey]);
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
        MangasPage(mangaList, searchResponse.result.hasNextPage())
    }

    private fun Document.extractBrowseResponse(): SearchResponse? {
        val initialData = selectFirst("script#initial-data")?.data() ?: return null
        val queries = runCatching {
            initialData.parseAs<JsonObject>()["queries"] as? JsonObject
        }.getOrNull() ?: return null

        return queries.values.firstNotNullOfOrNull { value ->
            runCatching { value.parseAs<SearchResponse>() }
                .getOrNull()
                ?.takeIf { it.result.items.isNotEmpty() }
        }
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("order[chapter_updated_at]", "desc")
            addQueryParameter("page", page.toString())
            applyBrowseContentPreferences()
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchMangaListFromBrowse(
        latestUpdatesRequest(page),
    )

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val withFilters = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("browse")
            .apply {
                filters.filterIsInstance<Filters.UriFilter>()
                    .forEach { it.addToUri(this) }

                // Author/Artist/Tags filters all expose a free-text input that
                // supports multiple comma-separated names. The API filters by
                // numeric IDs, so each name is looked up against /tags/search.
                filters.firstInstanceOrNull<Filters.AuthorFilter>()?.state
                    ?.let { resolveTagIdsForNames("author", it) }
                    ?.forEach { addQueryParameter("authors[]", it) }
                filters.firstInstanceOrNull<Filters.ArtistFilter>()?.state
                    ?.let { resolveTagIdsForNames("artist", it) }
                    ?.forEach { addQueryParameter("artists[]", it) }
                filters.firstInstanceOrNull<Filters.TagsFilter>()?.state
                    ?.let { resolveTagIdsForNames("tag", it) }
                    ?.forEach { addQueryParameter("genres_in[]", it) }
            }
            .build()

        val url = withFilters.newBuilder().apply {
            // Manual filters in the search sheet override the corresponding
            // source-level preference; otherwise fall back to the preference.
            if (withFilters.queryParameter("content_rating") == null) {
                applyContentRatingPreference()
            }
            if (withFilters.queryParameterValues("types[]").isEmpty()) {
                applyTypesPreference()
            }
            if (withFilters.queryParameterValues("demographics[]").isEmpty()) {
                applyDemographicsPreference()
            }
            // Blocked genres are ALWAYS applied (even alongside manual genre
            // include/exclude) — the helper itself skips any genre the user
            // explicitly INCLUDED in the search filter, so a one-off lookup
            // for a normally-blocked genre still works.
            applyBlockedGenresPreference()

            // The Match filter contributes `genres_mode`. If neither the
            // search filter nor the blocked-genres preference put any term
            // on the URL, drop the param so the site picks its own default.
            val hasTermSelection = build().queryParameterValues("genres_in[]").isNotEmpty() ||
                build().queryParameterValues("genres_ex[]").isNotEmpty()
            if (!hasTermSelection) {
                removeAllQueryParameters("genres_mode")
            }

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
                build().queryParameterNames
                    .filter { it.startsWith("order[") }
                    .forEach(::removeAllQueryParameters)
                addQueryParameter("sort", "relevance:desc")
            }

            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        titlePathFromQuery(query)?.let { titlePath ->
            return fetchMangaDetails(SManga.create().apply { url = titlePath })
                .map { MangasPage(listOf(it), false) }
        }

        return fetchMangaListFromBrowse(searchMangaRequest(page, query, filters))
    }

    private fun titlePathFromQuery(query: String): String? {
        val queryUrl = query.trim()
            .takeIf { it.isNotEmpty() }
            ?.toHttpUrlOrNull()
            ?: return null

        val host = queryUrl.host.removePrefix("www.")
        if (host != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (queryUrl.pathSegments.size < 2 || queryUrl.pathSegments[0] != "title") return null

        val mangaId = queryUrl.pathSegments[1].substringBefore("-")
        return mangaId.takeIf { it.isNotBlank() }?.let { "/$it" }
    }

    /**
     * Apply every content-related source-level preference (rating, types,
     * demographics, blocked genres) in one go. Used by popular/latest
     * where there's no search filter sheet to override anything.
     * `searchMangaRequest` calls each helper individually so the search
     * filter can short-circuit per-field.
     */
    private fun HttpUrl.Builder.applyBrowseContentPreferences() {
        applyContentRatingPreference()
        applyTypesPreference()
        applyDemographicsPreference()
        applyBlockedGenresPreference()
    }

    private fun HttpUrl.Builder.applyContentRatingPreference() {
        Filters.getContentRatingsUpTo(preferences.contentRating()).takeIf { it.isNotEmpty() }?.let {
            addQueryParameter("content_rating", it.joinToString(","))
        }
    }

    /**
     * Apply the source-level Default Types preference. The site treats no
     * `types[]` param as "show all", so we omit the filter when the user
     * has every type selected (the only state that would be a no-op
     * otherwise). An empty selection — meaning the user explicitly
     * unchecked everything — would hide every result; treat that as
     * "no preference" and skip too, since the alternative is a confusing
     * empty browse.
     */
    private fun HttpUrl.Builder.applyTypesPreference() {
        val selected = preferences.defaultTypes()
        val all = Filters.getTypes().map { it.second }.toSet()
        if (selected.isEmpty() || selected == all) return
        selected.forEach { addQueryParameter("types[]", it) }
    }

    /** Same shape as [applyTypesPreference] for Demographics. */
    private fun HttpUrl.Builder.applyDemographicsPreference() {
        val selected = preferences.defaultDemographics()
        val all = Filters.getDemographics().map { it.second }.toSet()
        if (selected.isEmpty() || selected == all) return
        selected.forEach { addQueryParameter("demographics[]", it) }
    }

    /**
     * Add a `genres_ex[]` for every genre the user listed in their Blocked
     * Genres preference, except those the search filter explicitly
     * INCLUDED (so a manual search for a normally-blocked genre still
     * works as a one-off escape hatch).
     */
    private fun HttpUrl.Builder.applyBlockedGenresPreference() {
        val blocked = preferences.blockedGenres()
        if (blocked.isEmpty()) return
        val explicitlyIncluded = build().queryParameterValues("genres_in[]").toSet()
        blocked.asSequence()
            .filter { it !in explicitlyIncluded }
            .forEach { addQueryParameter("genres_ex[]", it) }
    }

    /**
     * Resolves a free-text input — possibly several comma-separated names —
     * to the numeric IDs the site uses in `authors[]` / `artists[]` query
     * parameters. Each name is looked up via `/tags/search`; names that match
     * nothing simply contribute no IDs.
     */
    private fun resolveTagIdsForNames(type: String, raw: String): List<String> = raw
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { resolveTagIds(type, it) }

    private fun resolveTagIds(type: String, name: String): List<String> {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("tags")
            .addPathSegment("search")
            .addQueryParameter("type", type)
            .addQueryParameter("q", name)
            .build()

        return runCatching {
            client.newCall(GET(url, headers)).execute().use { response ->
                response.parseAs<TagSearchResponse>().result.map { it.id.toString() }
            }
        }.getOrDefault(emptyList())
    }

    // ============================== Filters ==============================
    override fun getFilterList() = Filters().getFilterList()

    // ============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.fromCallable {
        val document = runBlocking {
            client.newCall(GET(getMangaUrl(manga), headers)).awaitSuccess().asJsoup()
        }

        val initialData = document.selectFirst("script#initial-data")?.data()
            ?: throw Exception("Could not find manga data in page")
        val root = initialData.parseAs<JsonObject>()
        val queries = root["queries"] as? JsonObject
            ?: throw Exception("Could not find queries in manga data")
        val detail = queries.entries.firstOrNull { (key, _) -> key.contains("\"detail\"") }
            ?.value
            ?: throw Exception("Could not find manga detail in queries")

        detail.parseAs<Manga>().toSManga(
            preferences.posterQuality(),
            preferences.alternativeNamesInDescription(),
            preferences.scorePosition(),
            preferences.showExtraInfo(),
            preferences.showTagsInGenres(),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title${manga.url}"

    // ============================= Chapters ==============================
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val deduplicate = preferences.deduplicateChapters()
        val blacklist = preferences.scanlatorBlacklist()
        val mangaSlug = manga.url.removePrefix("/")

        val document = runBlocking {
            client.newCall(GET(getMangaUrl(manga), headers)).awaitSuccess().asJsoup()
        }
        val payload = runInWebView(
            document = document,
            buildScript = { interfaceName ->
                $$"""
                    (function () {
                        const payloadKey = '__comixChapterPayload';
                        if (window[payloadKey]?.installed) return null;

                        const state = window[payloadKey] = {
                            installed: true,
                            submitted: false,
                            seen: new Set(),
                            nextClicks: new Set(),
                            items: []
                        };
                        const submit = () => {
                            if (state.submitted) return;
                            state.submitted = true;
                            window.$$interfaceName.passPayload(JSON.stringify(state.items));
                        };
                        const capture = parsed => {
                            try {
                                const items = parsed?.result?.items;
                                const first = items?.[0];
                                if (
                                    state.submitted ||
                                    !Array.isArray(items) ||
                                    items.length === 0 ||
                                    first?.id === undefined ||
                                    first?.number === undefined
                                ) return false;

                                const meta = parsed.result.meta || parsed.result.pagination || {};
                                const page = meta.page || 1;
                                if (state.seen.has(page)) return true;

                                state.seen.add(page);
                                state.items.push(...items);
                                if (meta.hasNext && !state.nextClicks.has(page)) {
                                    state.nextClicks.add(page);
                                    window.$$interfaceName.resetTimer();
                                    let tries = 0;
                                    const interval = setInterval(() => {
                                        const button = document.querySelector('.mchap-foot button[aria-label*=Next]');
                                        if (button && !button.disabled) {
                                            button.click();
                                            clearInterval(interval);
                                        } else if (++tries > 50) {
                                            clearInterval(interval);
                                            submit();
                                        }
                                    }, 100);
                                } else {
                                    submit();
                                }
                                return true;
                            } catch (e) {
                                return false;
                            }
                        };

                        const originalParse = JSON.parse;
                        const proxiedParse = new Proxy(originalParse, {
                            apply(target, thisArg, args) {
                                const parsed = Reflect.apply(target, thisArg, args);
                                capture(parsed);
                                return parsed;
                            }
                        });
                        proxiedParse.__comixChapterCaptureInstalled = true;
                        JSON.parse = proxiedParse;

                        try {
                            const raw = document.querySelector('script#initial-data')?.textContent;
                            const queries = raw && originalParse(raw).queries;
                            if (queries) Object.values(queries).some(capture);
                        } catch (e) {}
                        return null;
                    })();
                """.trimIndent()
            },
        )

        val allChapters = payload.parseAs<List<Chapter>>()

        // Filter out groups specified in the blacklist first
        val filteredChapters = if (blacklist.isNotEmpty()) {
            allChapters.filter { ch ->
                val scanlatorName = when {
                    ch.group != null -> ch.group.name
                    ch.isOfficial -> "Official"
                    else -> "Unknown"
                }
                val nameNormalized = scanlatorName.trim().lowercase()
                val idStr = ch.group?.id?.toString()

                nameNormalized !in blacklist && idStr !in blacklist
            }
        } else {
            allChapters
        }

        val finalChapters: List<Chapter> = if (deduplicate) {
            val chapterMap = LinkedHashMap<Number, Chapter>()
            deduplicateChapters(chapterMap, filteredChapters)
            chapterMap.values.toList()
        } else {
            filteredChapters
        }

        finalChapters.map { it.toSChapter(mangaSlug) }
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun deduplicateChapters(
        chapterMap: LinkedHashMap<Number, Chapter>,
        items: List<Chapter>,
    ) {
        for (ch in items) {
            val key = ch.number
            val current = chapterMap[key]
            if (current == null) {
                chapterMap[key] = ch
            } else {
                val newIsOfficial = ch.isOfficial
                val currentIsOfficial = current.isOfficial
                val newIsGroup10702 = ch.group?.id == 10702
                val currentIsGroup10702 = current.group?.id == 10702

                val better = when {
                    newIsOfficial && !currentIsOfficial -> true
                    !newIsOfficial && currentIsOfficial -> false
                    newIsGroup10702 && !currentIsGroup10702 -> true
                    !newIsGroup10702 && currentIsGroup10702 -> false
                    else -> when {
                        ch.votes > current.votes -> true
                        ch.votes < current.votes -> false
                        else -> ch.id > current.id
                    }
                }
                if (better) chapterMap[key] = ch
            }
        }
    }

    // =============================== Pages ===============================
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val request = GET(getChapterUrl(chapter), headers)
        val document = runBlocking {
            client.newCall(request).awaitSuccess().asJsoup()
        }
        val payload = runInWebView(
            document = document,
            buildScript = { interfaceName ->
                """
                (function () {
                    const payloadKey = '__comixPagePayload';
                    const capture = parsed => {
                        try {
                            if (parsed && parsed.result && parsed.result.pages) {
                                window[payloadKey] = JSON.stringify(parsed);
                                window.$interfaceName.passPayload(window[payloadKey]);
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

        val pages = payload.parseAs<ChapterResponse>().result.pages
        val base = pages.baseUrl.trimEnd('/')

        pages.items.mapIndexed { index, img ->
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

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    private fun runInWebView(
        document: Document,
        initializationScript: String? = null,
        buildScript: (interfaceName: String) -> String,
    ): String {
        val handler = Handler(Looper.getMainLooper())
        val payloadResult = WebViewPayloadResult()
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
        val script = buildScript(interfaceName)
        val emptyResponse = WebResourceResponse("text/plain", "utf-8", Buffer().inputStream())
        val active = AtomicBoolean(true)
        val started = Semaphore(0)
        val startupError = AtomicReference<Throwable?>()

        var webView: WebView? = null
        var injectScript: Runnable? = null
        var lastUrl = document.location()
        handler.post {
            try {
                if (!active.get()) return@post

                val view = WebView(applicationContext)
                webView = view

                // Some WebView implementations do not support manual layout.
                runCatching {
                    view.layoutParams = ViewGroup.LayoutParams(WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(WEBVIEW_WIDTH, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(WEBVIEW_HEIGHT, View.MeasureSpec.EXACTLY),
                    )
                    view.layout(0, 0, WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
                }

                with(view.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    blockNetworkImage = false
                    userAgentString = headers["User-Agent"]
                }

                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(view, true)
                }

                view.addJavascriptInterface(payloadResult, interfaceName)

                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val requestUrl = request.url?.toString()?.toHttpUrlOrNull()
                            ?: return super.shouldInterceptRequest(view, request)

                        val allowedHost = requestUrl.host == "comix.to" ||
                            requestUrl.host.endsWith(".comix.to") ||
                            requestUrl.host == "challenges.cloudflare.com"
                        if (!allowedHost) return emptyResponse
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) lastUrl = url
                        if (active.get() && payloadResult.payload == null) {
                            runCatching { view.evaluateJavascript(script, null) }
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null) lastUrl = url
                        if (active.get() && payloadResult.payload == null) {
                            runCatching { view.evaluateJavascript(script, null) }
                        }
                    }
                }

                val retry = object : Runnable {
                    override fun run() {
                        if (!active.get() || payloadResult.payload != null) return
                        runCatching { view.evaluateJavascript(script, null) }
                        if (active.get() && payloadResult.payload == null) {
                            handler.postDelayed(this, SCRIPT_RETRY_INTERVAL_MS)
                        }
                    }
                }
                injectScript = retry

                val html = document.clone().apply {
                    initializationScript?.let {
                        head().prependElement("script").append(it)
                    }
                }.outerHtml()

                view.loadDataWithBaseURL(
                    document.location(),
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
                handler.post(retry)
            } catch (error: Throwable) {
                startupError.set(error)
            } finally {
                started.release()
            }
        }

        val completed = try {
            if (!started.tryAcquire(WEBVIEW_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw Exception("Timed out starting WebView (url=$lastUrl)")
            }
            startupError.get()?.let {
                throw Exception("Failed to start WebView (url=$lastUrl)", it)
            }
            payloadResult.await(WEBVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            active.set(false)
            handler.post {
                injectScript?.let(handler::removeCallbacks)
                val view = webView
                webView = null
                runCatching { view?.stopLoading() }
                runCatching { view?.destroy() }
            }
        }

        if (!completed) {
            throw Exception("Timed out waiting for WebView payload (url=$lastUrl)")
        }
        return payloadResult.payload ?: throw Exception("Failed to capture WebView payload")
    }

    private class WebViewPayloadResult {
        private val signal = Semaphore(0)

        @Volatile
        var payload: String? = null
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(data: String) {
            if (payload == null) {
                payload = data
                signal.release()
            }
        }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun resetTimer() {
            signal.release()
        }

        fun await(timeout: Long, unit: TimeUnit): Boolean {
            while (payload == null) {
                if (!signal.tryAcquire(timeout, unit)) return false
            }
            return true
        }
    }

    // ============================= Settings =============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
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

        // Content preferences (mirrors comix.to's "Content preferences" modal):
        //   - Default Types       — checkbox per type, all checked by default
        //   - Default Demographics — same, all checked by default
        //   - Blocked Genres      — opt-in list of genres to always exclude
        //
        // The first two omit the corresponding query param when ALL are checked
        // (= "no filter"). Any narrower selection sends `types[]` /
        // `demographics[]`. The Blocked Genres set adds `genres_ex[]` for each
        // entry. All three are overridable per-search via the existing filter
        // sheet, except blocked genres which always apply unless the search
        // filter explicitly INCLUDED the same genre.
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
                "comix.to itself shows on the page."
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
        private const val PREF_SCANLATOR_BLACKLIST = "pref_scanlator_blacklist"
        private const val ALTERNATIVE_NAMES_IN_DESCRIPTION = "pref_alt_names_in_description"
        private const val PREF_SHOW_EXTRA_INFO = "pref_show_extra_info"
        private const val PREF_SHOW_TAGS_IN_GENRES = "pref_show_tags_in_genres"
        private const val PREF_SCORE_POSITION = "pref_score_position"

        private const val DEFAULT_CONTENT_RATING = "suggestive"
        private const val WEBVIEW_START_TIMEOUT_SECONDS = 120L
        private const val WEBVIEW_TIMEOUT_SECONDS = 90L
        private const val SCRIPT_RETRY_INTERVAL_MS = 100L
        private const val WEBVIEW_WIDTH = 1080
        private const val WEBVIEW_HEIGHT = 1920
        private val SCRAMBLE_PATH_FALLBACK_REGEX = Regex("/(?:i5|s?i+)/")
        private val CHAPTER_NUM_REGEX = Regex("""Ch\.([\d.]+)""")
        private val GROUP_ID_REGEX = Regex("""/groups/(\d+)""")
        private val CHAPTER_ID_REGEX = Regex("""/(\d+)-""")
        private val CHAPTER_PAGINATION_REGEX = Regex("""Showing\s+\d+\s+to\s+(\d+)\s+of\s+(\d+)""")
    }
}
