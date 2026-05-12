package eu.kanade.tachiyomi.extension.en.comix

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Comix :
    HttpSource(),
    ConfigurableSource {

    override val name = "Comix"
    override val baseUrl = "https://comix.to"
    private val apiUrl = "https://comix.to/api/v1"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("order[score]", "desc")
            addQueryParameter("limit", "28")
            addQueryParameter("page", page.toString())
            applyContentPreferences()
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("order[chapter_updated_at]", "desc")
            addQueryParameter("limit", "28")
            addQueryParameter("page", page.toString())
            applyContentPreferences()
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val queryUrl = query.trim().toHttpUrlOrNull()
            if (queryUrl != null) {
                val host = queryUrl.host.removePrefix("www.")
                if (host == baseUrl.toHttpUrl().host.removePrefix("www.") && queryUrl.pathSegments.size >= 2 && queryUrl.pathSegments[0] == "title") {
                    val mangaId = queryUrl.pathSegments[1].substringBefore("-")
                    return mangaDetailsRequest(SManga.create().apply { this.url = "/$mangaId" })
                }
            }
        }

        val withFilters = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
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
                addQueryParameter("keyword", query)
                removeAllQueryParameters("order[score]")
                removeAllQueryParameters("order[chapter_updated_at]")
                addQueryParameter("order[relevance]", "desc")
            }

            addQueryParameter("limit", "28")
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    /**
     * Apply every content-related source-level preference (rating, types,
     * demographics, blocked genres) in one go. Used by popular/latest
     * where there's no search filter sheet to override anything.
     * `searchMangaRequest` calls each helper individually so the search
     * filter can short-circuit per-field.
     */
    private fun HttpUrl.Builder.applyContentPreferences() {
        applyContentRatingPreference()
        applyTypesPreference()
        applyDemographicsPreference()
        applyBlockedGenresPreference()
    }

    private fun HttpUrl.Builder.applyContentRatingPreference() {
        preferences.contentRating().takeIf { it.isNotEmpty() }?.let {
            addQueryParameter("content_rating", it)
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

    override fun searchMangaParse(response: Response): MangasPage {
        val posterQuality = preferences.posterQuality()

        val pathSegments = response.request.url.pathSegments
        val mangaIdx = pathSegments.indexOf("manga")

        if (mangaIdx != -1 && pathSegments.size > mangaIdx + 1 && !pathSegments.contains("chapters")) {
            val res: SingleMangaResponse = response.parseAs()
            val manga = listOf(res.result.toBasicSManga(posterQuality))
            return MangasPage(manga, false)
        } else {
            val res: SearchResponse = response.parseAs()
            val manga = res.result.items.map { it.toBasicSManga(posterQuality) }
            return MangasPage(manga, res.result.hasNextPage())
        }
    }

    // ============================== Filters ==============================
    override fun getFilterList() = Filters().getFilterList()

    // ============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val hid = manga.url.removePrefix("/").substringBefore("-")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(hid)
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaResponse: SingleMangaResponse = response.parseAs()

        return mangaResponse.result.toSManga(
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
        chapterListFromWebView(manga)
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    private fun chapterListFromWebView(manga: SManga): List<SChapter> {
        val deduplicate = preferences.deduplicateChapters()
        val mangaSlug = manga.url.removePrefix("/")

        val handler = Handler(Looper.getMainLooper())
        val signal = Semaphore(0)
        val done = AtomicBoolean(false)
        val jsInterface = ChapterListJsInterface(signal, done)
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
        val script = $$"""
            (function () {
                const rewriteUrl = function (url) {
                    if (typeof url === 'string' && url.indexOf('/chapters') !== -1 && /[?&]limit=\d+/.test(url)) {
                        return url.replace(/([?&]limit=)\d+/, '$1100');
                    }
                    return url;
                };
                const originalOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function (method, url) {
                    arguments[1] = rewriteUrl(url);
                    return originalOpen.apply(this, arguments);
                };
                const originalFetch = window.fetch;
                window.fetch = function (input, init) {
                    if (typeof input === 'string') {
                        input = rewriteUrl(input);
                    } else if (input && typeof input.url === 'string') {
                        const newUrl = rewriteUrl(input.url);
                        if (newUrl !== input.url) input = new Request(newUrl, input);
                    }
                    return originalFetch.call(this, input, init);
                };

                const originalParse = JSON.parse;
                const seen = new Set();
                JSON.parse = new Proxy(originalParse, {
                    apply(target, thisArg, args) {
                        const parsed = Reflect.apply(target, thisArg, args);
                        try {
                            if (
                                parsed && parsed.result &&
                                Array.isArray(parsed.result.items) &&
                                parsed.result.items.length > 0 &&
                                parsed.result.items[0] &&
                                parsed.result.items[0].id !== undefined &&
                                parsed.result.items[0].mangaId !== undefined
                            ) {
                                const meta = parsed.result.meta || parsed.result.pagination;
                                const page = (meta && meta.page) || 1;
                                if (!seen.has(page)) {
                                    seen.add(page);
                                    const hasNext = !!(meta && meta.hasNext);
                                    window.$$interfaceName.passPayload(args[0], hasNext);
                                    if (hasNext) {
                                        let tries = 0;
                                        const iv = setInterval(function () {
                                            const btn = document.querySelector('.mchap-foot button[aria-label*=Next]');
                                            if (btn && !btn.disabled) {
                                                btn.click();
                                                clearInterval(iv);
                                            } else if (++tries > 50) {
                                                clearInterval(iv);
                                            }
                                        }, 100);
                                    }
                                }
                            }
                        } catch (e) {}
                        return parsed;
                    }
                });
            })();
        """.trimIndent()

        var webView: WebView? = null
        handler.post {
            val view = WebView(Injekt.get<Application>())
            webView = view

            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockNetworkImage = true
                userAgentString = headers["User-Agent"]
            }
            view.addJavascriptInterface(jsInterface, interfaceName)

            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view.evaluateJavascript(script) {}
                }
            }

            view.loadUrl(getMangaUrl(manga))
        }

        var timedOut = false
        while (!done.get()) {
            if (!signal.tryAcquire(30, TimeUnit.SECONDS)) {
                timedOut = true
                break
            }
        }
        handler.post { webView?.destroy() }

        if (timedOut) throw Exception("Timed out waiting for chapter list")
        if (jsInterface.payloads.isEmpty()) throw Exception("Failed to capture chapter list")

        val allChapters = jsInterface.payloads.flatMap {
            it.parseAs<ChapterDetailsResponse>().result.items
        }

        val finalChapters: List<Chapter> = if (deduplicate) {
            val chapterMap = LinkedHashMap<Number, Chapter>()
            deduplicateChapters(chapterMap, allChapters)
            chapterMap.values.toList()
        } else {
            allChapters
        }

        return finalChapters.map { it.toSChapter(mangaSlug) }
    }

    private class ChapterListJsInterface(
        private val signal: Semaphore,
        private val done: AtomicBoolean,
    ) {
        private val _payloads = mutableListOf<String>()
        val payloads: List<String> get() = synchronized(_payloads) { _payloads.toList() }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(data: String, hasNext: Boolean) {
            synchronized(_payloads) { _payloads.add(data) }
            if (!hasNext) done.set(true)
            signal.release()
        }
    }

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
        pageListFromWebView(chapter)
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    private fun pageListFromWebView(chapter: SChapter): List<Page> {
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = PageListJsInterface(latch)
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
        val script = """
            (function () {
                const originalParse = JSON.parse;
                JSON.parse = new Proxy(originalParse, {
                    apply(target, thisArg, args) {
                        const parsed = Reflect.apply(target, thisArg, args);
                        try {
                            if (parsed && parsed.result && parsed.result.pages) {
                                window.$interfaceName.passPayload(args[0]);
                            }
                        } catch (e) {}
                        return parsed;
                    }
                });
            })();
        """.trimIndent()

        var webView: WebView? = null
        handler.post {
            val view = WebView(Injekt.get<Application>())
            webView = view

            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockNetworkImage = true
                userAgentString = headers["User-Agent"]
            }
            view.addJavascriptInterface(jsInterface, interfaceName)

            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view.evaluateJavascript(script) {}
                }
            }

            view.loadUrl(getChapterUrl(chapter))
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (!completed) throw Exception("Timed out waiting for page list")

        val payload = jsInterface.payload ?: throw Exception("Failed to capture page list")
        val res = payload.parseAs<ChapterResponse>()
        val result = res.result ?: throw Exception("Chapter not found")
        val pages = result.pages
        if (pages.items.isEmpty()) {
            throw Exception("No images found for chapter ${result.id}")
        }
        val base = pages.baseUrl.trimEnd('/')
        return pages.items.mapIndexed { index, img ->
            val full = if (img.url.startsWith("http")) img.url else "$base/${img.url.trimStart('/')}"
            Page(index, imageUrl = full)
        }
    }

    private class PageListJsInterface(private val latch: CountDownLatch) {
        @Volatile
        var payload: String? = null
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(data: String) {
            if (payload == null) {
                payload = data
                latch.countDown()
            }
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
        private const val ALTERNATIVE_NAMES_IN_DESCRIPTION = "pref_alt_names_in_description"
        private const val PREF_SHOW_EXTRA_INFO = "pref_show_extra_info"
        private const val PREF_SHOW_TAGS_IN_GENRES = "pref_show_tags_in_genres"
        private const val PREF_SCORE_POSITION = "pref_score_position"

        private const val DEFAULT_CONTENT_RATING = "suggestive"
    }
}
