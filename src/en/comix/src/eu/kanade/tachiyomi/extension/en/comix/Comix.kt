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

    override val client = network.client.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

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
                filters.filterIsInstance<Filters.UriFilter>().forEach { it.addToUri(this) }
                filters.firstInstanceOrNull<Filters.AuthorFilter>()?.state
                    ?.let { resolveTagIdsForNames("author", it) }?.forEach { addQueryParameter("authors[]", it) }
                filters.firstInstanceOrNull<Filters.ArtistFilter>()?.state
                    ?.let { resolveTagIdsForNames("artist", it) }?.forEach { addQueryParameter("artists[]", it) }
                filters.firstInstanceOrNull<Filters.TagsFilter>()?.state
                    ?.let { resolveTagIdsForNames("tag", it) }?.forEach { addQueryParameter("genres_in[]", it) }
            }.build()
        val url = withFilters.newBuilder().apply {
            if (withFilters.queryParameter("content_rating") == null) applyContentRatingPreference()
            if (withFilters.queryParameterValues("types[]").isEmpty()) applyTypesPreference()
            if (withFilters.queryParameterValues("demographics[]").isEmpty()) applyDemographicsPreference()
            applyBlockedGenresPreference()
            val hasTermSelection = build().queryParameterValues("genres_in[]").isNotEmpty() || build().queryParameterValues("genres_ex[]").isNotEmpty()
            if (!hasTermSelection) removeAllQueryParameters("genres_mode")
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

    private fun HttpUrl.Builder.applyContentPreferences() {
        applyContentRatingPreference()
        applyTypesPreference()
        applyDemographicsPreference()
        applyBlockedGenresPreference()
    }
    private fun HttpUrl.Builder.applyContentRatingPreference() {
        preferences.contentRating().takeIf { it.isNotEmpty() }?.let { addQueryParameter("content_rating", it) }
    }
    private fun HttpUrl.Builder.applyTypesPreference() {
        val selected = preferences.defaultTypes()
        val all = Filters.getTypes().map { it.second }.toSet()
        if (selected.isEmpty() || selected == all) return
        selected.forEach { addQueryParameter("types[]", it) }
    }
    private fun HttpUrl.Builder.applyDemographicsPreference() {
        val selected = preferences.defaultDemographics()
        val all = Filters.getDemographics().map { it.second }.toSet()
        if (selected.isEmpty() || selected == all) return
        selected.forEach { addQueryParameter("demographics[]", it) }
    }
    private fun HttpUrl.Builder.applyBlockedGenresPreference() {
        val blocked = preferences.blockedGenres()
        if (blocked.isEmpty()) return
        val explicitlyIncluded = build().queryParameterValues("genres_in[]").toSet()
        blocked.asSequence().filter { it !in explicitlyIncluded }.forEach { addQueryParameter("genres_ex[]", it) }
    }
    private fun resolveTagIdsForNames(type: String, raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { resolveTagIds(type, it) }
    private fun resolveTagIds(type: String, name: String): List<String> {
        val url = apiUrl.toHttpUrl().newBuilder().addPathSegment("tags").addPathSegment("search")
            .addQueryParameter("type", type).addQueryParameter("q", name).build()
        return runCatching { client.newCall(GET(url, headers)).execute().use { response -> response.parseAs<TagSearchResponse>().result.map { it.id.toString() } } }.getOrDefault(emptyList())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val posterQuality = preferences.posterQuality()
        val pathSegments = response.request.url.pathSegments
        val mangaIdx = pathSegments.indexOf("manga")
        if (mangaIdx != -1 && pathSegments.size > mangaIdx + 1 && !pathSegments.contains("chapters")) {
            val res: SingleMangaResponse = response.parseAs()
            return MangasPage(listOf(res.result.toBasicSManga(posterQuality)), false)
        } else {
            val res: SearchResponse = response.parseAs()
            return MangasPage(res.result.items.map { it.toBasicSManga(posterQuality) }, res.result.hasNextPage())
        }
    }

    override fun getFilterList() = Filters().getFilterList()

    override fun mangaDetailsRequest(manga: SManga): Request {
        val hid = manga.url.removePrefix("/").substringBefore("-")
        return GET(apiUrl.toHttpUrl().newBuilder().addPathSegment("manga").addPathSegment(hid).build(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaResponse: SingleMangaResponse = response.parseAs()
        return mangaResponse.result.toSManga(preferences.posterQuality(), preferences.alternativeNamesInDescription(), preferences.scorePosition(), preferences.showExtraInfo(), preferences.showTagsInGenres())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title${manga.url}"

    // ==================== Chapter list via WebView interception ====================
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        WEBVIEW_SEMAPHORE.acquire()
        try {
            chapterListFromWebView(manga)
        } finally {
            WEBVIEW_SEMAPHORE.release()
        }
    }
    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    private fun chapterListFromWebView(manga: SManga): List<SChapter> {
        val deduplicate = preferences.deduplicateChapters()
        val mangaSlug = manga.url.removePrefix("/")
        val titlePath = "/title" + (if (manga.url.startsWith("/")) manga.url else "/${manga.url}")

        // A cold WebView load of the title route (/title/{slug}) never fires the
        // encrypted /chapters request — the React component that requests it on
        // mount stays silent in an offscreen WebView, even though the reader
        // route loads fine in the same WebView. A client-side (SPA) navigation
        // into the title route, on the other hand, always fires it. So we boot
        // the app on a reader page (proven to work) and then navigate to the
        // title route from inside the page, which triggers the chapter fetch
        // through the site's own decrypting client. Our hooks then capture the
        // decrypted JSON exactly as they do for the page list.
        val hid = manga.url.removePrefix("/").substringBefore("-")
        val detailsUrl = apiUrl.toHttpUrl().newBuilder().addPathSegment("manga").addPathSegment(hid).build()
        val boot = client.newCall(GET(detailsUrl, headers)).execute().use { it.parseAs<MangaBootUrls>() }.result
        val bootChapterUrl = boot.bootChapterUrl
            ?: if (!boot.hasChapters) return emptyList() else throw Exception("No chapter URL to load")
        val bootUrl = baseUrl + bootChapterUrl

        val handler = Handler(Looper.getMainLooper())
        val signal = Semaphore(0)
        val done = AtomicBoolean(false)
        val jsInterface = ChapterListJsInterface(signal, done)
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random()).map { pool.random() }.joinToString("")

        // Single self-contained script, injected on the reader (boot) page. It:
        //   1. hooks JSON.parse / fetch / XHR to capture decrypted chapter pages,
        //   2. clicks the breadcrumb link to the title route (real SPA navigation,
        //      which is what makes /chapters fire),
        //   3. drives the numbered pager ("Next page") to walk every page.
        // Because SPA navigation keeps the same document, the hooks installed
        // here persist across the navigation — no re-injection needed.
        // Only payloads whose items carry a chapter id + url + number are passed
        // up, so the reader page's own responses (chapter-indexes, comments,
        // page list) are ignored.
        val script = """
            (function(){
                if(window.__comixInit)return;window.__comixInit=true;
                var _seen={},_IFACE=window.$interfaceName,TITLE_PATH=${"\""}$titlePath${"\""};
                var gotFirst=false,lastHasNext=false,curPage=0;
                var _p=JSON.parse;
                function _isChapters(o){
                    if(!(o&&o.result&&o.result.items&&o.result.items.length))return false;
                    var it=o.result.items[0];
                    return it&&('id' in it)&&('url' in it)&&('number' in it);
                }
                // _handle takes an ALREADY-PARSED object plus its source text. It must
                // never call the (wrapped) JSON.parse, or the JSON.parse hook below
                // would recurse into itself on every chapter payload.
                function _handle(o,t){
                    try{
                    if(!_isChapters(o))return;
                    var m=o.result.meta||o.result.pagination||{},p=m.page||1;
                    gotFirst=true;lastHasNext=!!m.hasNext;if(p>curPage)curPage=p;
                    if(_seen[p])return;_seen[p]=true;_IFACE.passPayload(t,!!m.hasNext);
                    }catch(e){}
                }
                JSON.parse=function(){var r=_p.apply(this,arguments);try{if(typeof arguments[0]==='string')_handle(r,arguments[0]);}catch(e){}return r;};
                function _tryText(t){try{if(typeof t==='string')_handle(_p(t),t);}catch(e){}}
                var _f=window.fetch;window.fetch=function(i,d){return _f.call(this,i,d).then(function(r){try{r.clone().text().then(_tryText);}catch(e){}return r;});};
                var _x=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){var s=this;s.addEventListener('load',function(){_tryText(s.responseText);});return _x.apply(this,arguments);};

                function _titleLink(){
                    var a=document.querySelectorAll('a[href]');
                    for(var i=0;i<a.length;i++){try{if(new URL(a[i].href,location.origin).pathname===TITLE_PATH)return a[i];}catch(e){}}
                    return null;
                }
                var navTries=0,navTimer=setInterval(function(){
                    var a=_titleLink();
                    if(a){clearInterval(navTimer);a.click();_startPager();}
                    else if(++navTries>80){clearInterval(navTimer);}
                },300);

                function _startPager(){
                    var acted=-1,idle=0,safety=0;
                    var t=setInterval(function(){
                        if(++safety>500){clearInterval(t);return;}
                        if(!gotFirst)return;
                        if(!lastHasNext){clearInterval(t);return;}
                        if(acted===curPage){
                            if(++idle>14){acted=-1;idle=0;}
                            return;
                        }
                        var n=document.querySelector('.npager__nav[aria-label="Next page"]');
                        if(n&&!n.disabled){acted=curPage;idle=0;n.click();}
                    },700);
                }
            })();
        """.trimIndent()

        var webView: WebView? = null
        handler.post {
            val view = WebView(Injekt.get<Application>())
            webView = view
            view.layout(0, 0, 1920, 1080)
            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockNetworkImage = true // chapter list needs no images
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/27.0 Chrome/125.0.0.0 Mobile Safari/537.36"
            }
            view.addJavascriptInterface(jsInterface, interfaceName)
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view.evaluateJavascript(script) {}
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript(script) {}
                }
            }
            view.loadUrl(bootUrl)
        }

        var timedOut = false
        while (!done.get()) {
            if (!signal.tryAcquire(60, TimeUnit.SECONDS)) {
                timedOut = true
                break
            }
        }
        handler.post { webView?.destroy() }
        if (timedOut) throw Exception("Timed out loading chapters")
        if (jsInterface.payloads.isEmpty()) throw Exception("No chapter data captured")

        val allChapters = jsInterface.payloads.flatMap { it.parseAs<ChapterDetailsResponse>().result.items }
        val finalChapters: List<Chapter> = if (deduplicate) {
            val chapterMap = LinkedHashMap<Number, Chapter>()
            deduplicateChapters(chapterMap, allChapters)
            chapterMap.values.toList()
        } else {
            allChapters
        }
        return finalChapters.map { it.toSChapter(mangaSlug) }
    }

    private class ChapterListJsInterface(private val signal: Semaphore, private val done: AtomicBoolean) {
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

    private fun deduplicateChapters(chapterMap: LinkedHashMap<Number, Chapter>, items: List<Chapter>) {
        for (ch in items) {
            val key = ch.number
            val current = chapterMap[key]
            if (current == null) {
                chapterMap[key] = ch
                continue
            }
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

    // ==================== Page list via WebView interception ====================
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        WEBVIEW_SEMAPHORE.acquire()
        try {
            pageListFromWebView(chapter)
        } finally {
            WEBVIEW_SEMAPHORE.release()
        }
    }
    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    private fun pageListFromWebView(chapter: SChapter): List<Page> {
        if (chapter.url.substringAfter("/").substringBeforeLast("/").indexOf("-") == -1) {
            throw Exception("Outdated chapter URL. Please refresh the chapter list")
        }
        val chapterId = chapter.url.substringAfterLast("/").substringBefore("-")
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = PageListJsInterface(latch)
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random()).map { pool.random() }.joinToString("")
        val script = """
            (function(){
                var _done=false,_IFACE=window.$interfaceName;
                function _try(t){if(_done)return;try{var o=JSON.parse(t);if(o&&o.result&&o.result.pages){_done=true;_IFACE.passPayload(t);}}catch(e){}}
                var _p=JSON.parse;JSON.parse=function(){var r=_p.apply(this,arguments);try{if(!_done&&r&&r.result&&r.result.pages){_done=true;_IFACE.passPayload(arguments[0]);}}catch(e){}return r;};
                var _f=window.fetch;window.fetch=function(i,d){return _f.call(this,i,d).then(function(r){r.clone().text().then(_try);return r;});};
                var _x=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){var s=this;s.addEventListener('load',function(){_try(s.responseText);});return _x.apply(this,arguments);};
            })();
        """.trimIndent()
        var webView: WebView? = null
        handler.post {
            val view = WebView(Injekt.get<Application>())
            webView = view
            view.layout(0, 0, 800, 600)
            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockNetworkImage = false
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/27.0 Chrome/125.0.0.0 Mobile Safari/537.36"
            }
            view.addJavascriptInterface(jsInterface, interfaceName)
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view.evaluateJavascript(script) {}
                }
                override fun onPageFinished(view: WebView, url: String?) {
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
        if (result.pages.items.isEmpty()) throw Exception("No images for chapter ${result.id}")
        val base = result.pages.baseUrl.trimEnd('/')
        return result.pages.items.mapIndexed { index, img ->
            val full = if (img.url.startsWith("http")) img.url else "$base/${img.url.trimStart('/')}"
            Page(index, imageUrl = full)
        }
    }

    private class PageListJsInterface(private val latch: CountDownLatch) {
        @Volatile var payload: String? = null
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

    // ==================== Settings ====================
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
            summary = "Maximum content rating shown in popular, latest, and search results. The Content rating filter in search overrides this. Current: %s."
            entries = arrayOf("Show all", "Safe only", "Up to Suggestive", "Up to Erotica", "Up to Pornographic")
            entryValues = arrayOf("", "safe", "suggestive", "erotica", "pornographic")
            setDefaultValue(DEFAULT_CONTENT_RATING)
        }.let(screen::addPreference)
        MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_TYPES
            title = "Default types"
            summary = "Types to include in popular, latest, and search results. The Type filter in search overrides this."
            entries = Filters.getTypes().map { it.first }.toTypedArray()
            entryValues = Filters.getTypes().map { it.second }.toTypedArray()
            setDefaultValue(Filters.getTypes().map { it.second }.toSet())
        }.let(screen::addPreference)
        MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_DEMOGRAPHICS
            title = "Default demographics"
            summary = "Demographics to include in popular, latest, and search results. The Demographic filter in search overrides this."
            entries = Filters.getDemographics().map { it.first }.toTypedArray()
            entryValues = Filters.getDemographics().map { it.second }.toTypedArray()
            setDefaultValue(Filters.getDemographics().map { it.second }.toSet())
        }.let(screen::addPreference)
        MultiSelectListPreference(screen.context).apply {
            key = PREF_BLOCKED_GENRES
            title = "Blocked genres"
            summary = "Genres always excluded from results. The search filter can still include a blocked genre as a one-off override."
            entries = Filters.getGenres().map { it.first }.toTypedArray()
            entryValues = Filters.getGenres().map { it.second }.toTypedArray()
            setDefaultValue(emptySet<String>())
        }.let(screen::addPreference)
        SwitchPreferenceCompat(screen.context).apply {
            key = DEDUPLICATE_CHAPTERS
            title = "Deduplicate Chapters"
            summary = "Remove duplicate chapters from the chapter list.\nOfficial chapters (Comix-marked) are preferred, followed by the highest-voted or most recent.\nWarning: It can be slow on large lists."
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
            summary = "Append publication year, language, content rating, rank, ratings count, and follower count to the manga description."
            setDefaultValue(true)
        }.let(screen::addPreference)
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_TAGS_IN_GENRES
            title = "Show tags in genre chips"
            summary = "Include the site's narrative tag list (e.g. Demons, Vampires, Time Travel) alongside the curated genres in the manga details. Off by default — the curated set matches what comix.to itself shows on the page."
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
    private fun SharedPreferences.contentRating(): String {
        if (contains(PREF_CONTENT_RATING)) return getString(PREF_CONTENT_RATING, DEFAULT_CONTENT_RATING) ?: DEFAULT_CONTENT_RATING
        if (contains(LEGACY_HIDE_NSFW_PREF) && !getBoolean(LEGACY_HIDE_NSFW_PREF, true)) return ""
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

        // Limit concurrent WebView instances — spawning dozens of WebViews
        // simultaneously during library updates overwhelms the renderer pool
        // and triggers rate-limiting / Cloudflare challenges.
        private val WEBVIEW_SEMAPHORE = Semaphore(2)
    }
}
