package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Jinmantiantang :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest: Boolean = true

    private val preferences = getPreferences { preferenceMigration() }

    override val baseUrl: String = "https://" + preferences.baseUrl

    private val updateUrlInterceptor = UpdateUrlInterceptor(preferences)

    // 处理URL请求
    override val client: OkHttpClient = network.client
        .newBuilder()
        .apply { interceptors().add(0, updateUrlInterceptor) }
        .addInterceptor(ScrambledImageInterceptor)
        // Add rate limit to fix manga thumbnail load failure
        .rateLimit(
            preferences.getString(MAINSITE_RATELIMIT_PREF, MAINSITE_RATELIMIT_PREF_DEFAULT)!!.toInt(),
            preferences.getString(MAINSITE_RATELIMIT_PERIOD, MAINSITE_RATELIMIT_PERIOD_DEFAULT)!!.toLong().seconds,
        ) { it.host == baseUrl.toHttpUrl().host }
        .build()

    // 添加额外的header增加规避Cloudflare可能性
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .setRandomUserAgent()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        maybeAutoCheckIn()
        return super.fetchPopularManga(page)
    }

    // 点击量排序(人气)
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/albums?o=mv&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.list-col > div.p-b-15:not([data-group])").map { element ->
            popularMangaFromElement(element)
        }.filterGenre()
        val hasNextPage = document.selectFirst("a.prevnext") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun List<SManga>.filterGenre(): List<SManga> {
        val removedGenres = preferences.getString(BLOCK_PREF, "")!!.substringBefore("//").trim()
        if (removedGenres.isEmpty()) return this
        val removedList = removedGenres.lowercase().split(' ')
        return this.filterNot { manga ->
            manga.genre.orEmpty().lowercase().split(", ").any { removedList.contains(it) }
        }
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val children = element.children()
        if (children.isNotEmpty() && children[0].tagName() == "a") children.removeAt(0)
        if (children.size >= 4) {
            title = children[1].text()
            children[0].selectFirst("a")?.attr("href")?.let { setUrlWithoutDomain(it) }
            val img = children[0].selectFirst("img")
            if (img != null) {
                thumbnail_url = img.extractThumbnailUrl().substringBeforeLast('?')
            }
            author = children[2].select("a").joinToString(", ") { it.text() }
            genre = children[3].select("a").joinToString(", ") { it.text() }
        }
    }

    // 最新排序
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/albums?o=mr&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // For JinmantiantangUrlActivity
    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/album/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val sManga = mangaDetailsParse(response)
        sManga.url = "/album/$id/"
        return MangasPage(listOf(sManga), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val favoritesFilter = filters.filterIsInstance<FavoritesFilter>().firstOrNull()
        if (favoritesFilter?.isEnabled() == true) {
            return fetchFavorites(page)
        }
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported url")
            }
            val titleid = url.pathSegments[1]
            return fetchSearchManga(page, "$PREFIX_ID_SEARCH$titleid", filters)
        }
        return if (query.startsWith(PREFIX_ID_SEARCH_NO_COLON, true) || query.toIntOrNull() != null) {
            val id = query.removePrefix(PREFIX_ID_SEARCH_NO_COLON).removePrefix(":")
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    // 查询信息
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var params = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }

        val url = if (query.isNotEmpty() && !query.contains("-")) {
            // 禁漫天堂特有搜索方式: A +B --> A and B, A B --> A or B
            var newQuery = query.replace("+", "%2B").replace(" ", "+")
            // remove illegal param
            params = params.substringAfter("?")
            if (params.contains("search_query")) {
                val keyword = params.substringBefore("&").substringAfter("=")
                newQuery = "$newQuery+%2B$keyword"
                params = params.substringAfter("&")
            }
            "$baseUrl/search/photos?search_query=$newQuery&page=$page&$params"
        } else {
            params = if (params.isEmpty()) "/albums?" else params
            if (query.isEmpty()) {
                "$baseUrl$params&page=$page"
            } else {
                // 在搜索栏的关键词前添加-号来实现对筛选结果的过滤, 像 "-YAOI -扶他 -毛絨絨 -獵奇", 注意此时搜索功能不可用.
                val removedGenres = query.split(" ").filter { it.startsWith("-") }.joinToString("+") { it.removePrefix("-") }
                "$baseUrl$params&page=$page&screen=$removedGenres"
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // 收藏夹
    private fun fetchFavorites(page: Int): Observable<MangasPage> {
        val cached = getUsername()
        if (cached.isNotBlank()) {
            return favoritesObservable(page, cached)
        }
        return detectUsername().flatMap { detected ->
            if (detected.isBlank()) {
                Observable.error(Exception("未检测到登录状态：请在应用内置浏览器中打开网页并登录，或在插件设置中手动填写用户名"))
            } else {
                preferences.edit().putString(USERNAME_PREF, detected).apply()
                favoritesObservable(page, detected)
            }
        }
    }

    private fun favoritesObservable(page: Int, username: String): Observable<MangasPage> = client.newCall(favoritesRequest(page, username))
        .asObservableSuccess()
        .map { response -> favoritesParse(response) }

    private fun favoritesRequest(page: Int, username: String): Request = GET("$baseUrl/user/$username/favorite/albums?page=$page", headers)

    private fun favoritesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(FAVORITE_MANGA_SELECTOR).map { favoriteMangaFromElement(it) }
        if (mangas.isEmpty() && !document.isLoggedIn()) {
            throw Exception("登录已过期，请重新在应用内置浏览器中登录")
        }
        val hasNextPage = document.selectFirst("a.prevnext") != null
        return MangasPage(mangas, hasNextPage)
    }

    // 登录守卫：站点对未登录请求返回登录页，直接解析会得到空标题/空章节并写回书架，
    // 因此详情与章节请求在未登录时必须阻断。
    private val lastLoginNoticeAt = AtomicLong(0)

    private fun Document.isLoggedIn(): Boolean = selectFirst("#Comic_Top_Nav")?.selectFirst("a[href*='favorite'], a[href*='logout']") != null

    private fun requireLoggedIn(document: Document) {
        if (document.isLoggedIn()) return
        notifyLoginBlocked()
        throw Exception("未登录或登录已过期，已阻断请求以保护书架数据")
    }

    private fun notifyLoginBlocked() {
        val now = System.currentTimeMillis()
        val last = lastLoginNoticeAt.get()
        if (now - last < LOGIN_NOTICE_INTERVAL) return
        if (!lastLoginNoticeAt.compareAndSet(last, now)) return
        showToast("未登录，已阻断与站点的连接")
    }

    private fun favoriteMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a[href*='/album/']")
        if (link != null) {
            setUrlWithoutDomain(link.attr("href").substringBefore("?"))
            title = element.selectFirst(".video-title")?.text()?.trim() ?: "Unknown"
            val img = element.selectFirst(".thumb-overlay img")
            thumbnail_url = if (img != null) {
                when {
                    img.hasAttr("data-original") -> img.absUrl("data-original")
                    img.hasAttr("src") -> img.absUrl("src")
                    img.hasAttr("data-cfsrc") -> img.absUrl("data-cfsrc")
                    else -> ""
                }.substringBeforeLast('?')
            } else {
                ""
            }
        } else {
            title = "Unknown"
        }
    }

    // 用户名获取
    private fun getUsername(): String = preferences.getString(USERNAME_PREF, "")?.trim() ?: ""

    private val detectionInFlight = AtomicBoolean(false)

    private fun triggerUsernameDetection() {
        if (getUsername().isNotBlank()) return
        if (!detectionInFlight.compareAndSet(false, true)) return
        detectUsername().subscribe(
            { detected ->
                detectionInFlight.set(false)
                if (detected.isNotBlank()) {
                    preferences.edit().putString(USERNAME_PREF, detected).apply()
                }
            },
            { detectionInFlight.set(false) },
        )
    }

    private fun detectUsername(): Observable<String> = Observable.create { subscriber ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            val context = Injekt.get<Application>()
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.databaseEnabled = true

            var done = false
            val finish: (String?) -> Unit = { result ->
                if (!done) {
                    done = true
                    mainHandler.removeCallbacksAndMessages(null)
                    webView.stopLoading()
                    webView.destroy()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(result ?: "")
                        subscriber.onCompleted()
                    }
                }
            }
            val timeout = Runnable { finish(null) }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript(USERNAME_EXTRACTION_JS) { value ->
                        val result = parseJsString(value)
                        if (result != null) {
                            finish(result)
                        } else if (url != null && url.contains("challenge", ignoreCase = true)) {
                            // 等待 Cloudflare 验证自动通过
                        } else {
                            finish(null)
                        }
                    }
                }
            }

            mainHandler.postDelayed(timeout, DETECTION_TIMEOUT)
            webView.loadUrl(baseUrl)
        }
    }

    private fun parseJsString(value: String?): String? {
        if (value.isNullOrBlank() || value.trim() == "null") return null
        val trimmed = value.trim()
        return if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        } else {
            trimmed
        }
    }

    // 漫画详情
    private fun mangaDetailsResolve(response: Response): Document {
        val document = response.asJsoup()
        val scripts =
            document.select("#wrapper > script:containsData(function base64DecodeUtf8):containsData(document.write(html))")

        for (script in scripts) {
            val jsCode = script.html().trim()

            jsCode.lines().forEach { line ->
                val trimmedLine = line.trim()
                // html = base64DecodeUtf8("...")
                if (trimmedLine.startsWith("const html") || trimmedLine.startsWith("let html") || trimmedLine.startsWith(
                        "var html",
                    )
                ) {
                    val start =
                        trimmedLine.indexOf("base64DecodeUtf8(\"") + "base64DecodeUtf8(\"".length
                    val end = trimmedLine.indexOf("\");", start)
                    if (start > 0 && end > start) {
                        val html = Base64.decode(trimmedLine.substring(start, end), Base64.DEFAULT)
                        document.body().append(String(html))
                    }
                }
            }
        }
        return document
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = mangaDetailsResolve(response)
        requireLoggedIn(document)
        return mangaDetailsParse(document)
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text() ?: ""
        // keep thumbnail_url same as the one in popularMangaFromElement()
        val img = document.selectFirst(".thumb-overlay > img")
        thumbnail_url = img?.extractThumbnailUrl()?.substringBeforeLast('.') + "_3x4.jpg"
        author = selectAuthor(document)
        genre = selectDetailsStatusAndGenre(document, 0).trim().split(" ").joinToString(", ")

        // When the index passed by the "selectDetailsStatusAndGenre(document: Document, index: Int)" index is 1,
        // it will definitely return a String type of 0, 1 or 2. This warning can be ignored
        status = selectDetailsStatusAndGenre(document, 1).trim().toIntOrNull() ?: 0
        description = document.selectFirst("#intro-block .p-t-5.p-b-5")?.text()?.substringAfter("敘述：")?.trim() ?: ""
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    private fun Element.extractThumbnailUrl(): String = when {
        hasAttr("data-original") -> attr("data-original")
        hasAttr("src") -> attr("src")
        hasAttr("data-cfsrc") -> attr("data-cfsrc")
        else -> ""
    }

    // 查询作者信息
    private fun selectAuthor(document: Document): String {
        val elements = document.select("div.panel-body div.tag-block")
        if (elements.size > 3) {
            return elements[3].select(".btn-primary").joinToString { it.text() }
        }
        return ""
    }

    // 查询漫画状态和类别信息
    private fun selectDetailsStatusAndGenre(document: Document, index: Int): String {
        var status = "0"
        var genre = ""
        val spanGenres = document.select("span[itemprop=genre] a")
        if (spanGenres.isEmpty()) {
            return if (index == 1) status else genre
        }
        val elements: Elements = document.selectFirst("span[itemprop=genre]")?.select("a") ?: return ""
        for (value in elements) {
            when (val vote: String = value.select("a").text()) {
                "連載中" -> status = "1"
                "完結" -> status = "2"
                else -> genre = "$genre$vote "
            }
        }
        return if (index == 1) status else genre
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.selectFirst("a")?.attr("href") ?: ""
        name = element.selectFirst("a li h3")?.ownText() ?: ""
        date_upload = dateFormat.tryParse(element.selectFirst("a li span.hidden-xs")?.text())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = mangaDetailsResolve(response)
        requireLoggedIn(document)
        val elements = document.select("div[id=episode-block] a[href^=/photo/]")
        if (elements.isEmpty()) {
            val singleChapter = SChapter.create().apply {
                name = "单章节"
                url = document.selectFirst("#album_photo_cover > div.thumb-overlay > a")?.attr("href") ?: ""
                date_upload = dateFormat.tryParse(document.select("[itemprop=datePublished]").last()?.attr("content"))
            }
            return listOf(singleChapter)
        }
        return elements.map { chapterFromElement(it) }.reversed()
    }

    // 漫画图片信息
    override fun pageListParse(response: Response): List<Page> {
        tailrec fun internalParse(document: Document, pages: MutableList<Page>): List<Page> {
            val elements = document.select("div[class=center scramble-page spnotice_chk][id*=0]")
            for (element in elements) {
                val img = element.selectFirst("img") ?: continue
                val src = img.attr("src")
                val dataCfsrc = img.attr("data-cfsrc")
                val imageUrl = if (src.contains("blank.jpg") || dataCfsrc.contains("blank.jpg")) {
                    img.attr("data-original").substringBefore("?")
                } else {
                    src.substringBefore("?")
                }
                pages.add(Page(pages.size, imageUrl = imageUrl))
            }
            val next = document.selectFirst("a.prevnext")
            return if (next == null) {
                pages
            } else {
                internalParse(client.newCall(GET(next.attr("abs:href"), headers)).execute().asJsoup(), pages)
            }
        }

        return internalParse(response.asJsoup(), mutableListOf())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList {
        triggerUsernameDetection()
        return FilterList(
            FavoritesFilter(),
            Filter.Separator(),
            CategoryGroup(),
            SortFilter(),
            TimeFilter(),
            TypeFilter(),
        )
    }

    // 签到
    private class AlreadyCheckedInException : Exception()

    private fun maybeAutoCheckIn() {
        if (!preferences.getBoolean(CHECKIN_PREF, false)) return
        if (preferences.getString(CHECKIN_DATE_PREF, "") == today()) return
        Thread {
            try {
                val msg = performCheckIn()
                preferences.edit().putString(CHECKIN_DATE_PREF, today()).apply()
                showToast("签到成功：$msg")
            } catch (_: AlreadyCheckedInException) {
                preferences.edit().putString(CHECKIN_DATE_PREF, today()).apply()
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun performCheckIn(): String {
        val homepage = client.newCall(GET("$baseUrl/", headers)).execute().asJsoup()
        val nav = homepage.selectFirst("#Comic_Top_Nav")
        if (nav?.selectFirst("a[href*='favorite'], a[href*='logout']") == null) {
            throw Exception("登录已过期，请重新在应用内置浏览器中登录")
        }
        val dailyId = homepage.selectFirst("#bouns-popup")?.attr("data-dailyid")
            ?: throw Exception("未找到签到入口")
        val eventJson = client.newCall(
            GET("$baseUrl/ajax/user_daily_event?daily_id=$dailyId", headers),
        ).execute().body.string()
        val oldStep = Regex("\"oldStep\":(\\d+)").find(eventJson)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val response = client.newCall(
            POST(
                "$baseUrl/ajax/user_daily_sign",
                headers,
                FormBody.Builder()
                    .add("daily_id", dailyId)
                    .add("oldStep", (oldStep + 1).toString())
                    .build(),
            ),
        ).execute()
        if (!response.isSuccessful) {
            throw Exception("签到请求失败")
        }
        val signJson = response.body.string()
        val error = extractJsonString(signJson, "error")
        if (error == "finished") throw AlreadyCheckedInException()
        val msg = extractJsonString(signJson, "msg")
        if (msg != null && (msg.contains("已經完成每日簽到") || msg.contains("已经完成每日签到"))) {
            return msg
        }
        throw Exception(msg ?: "签到失败，请检查是否已登录")
    }

    private fun extractJsonString(json: String, field: String): String? {
        val m = Regex("\"$field\":\"((?:\\\\.|[^\"\\\\])*)\"").find(json) ?: return null
        return unescapeJson(m.groupValues[1])
    }

    private fun unescapeJson(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'u' -> {
                        if (i + 5 < s.length) {
                            val code = s.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                            } else {
                                sb.append("\\u")
                                i += 2
                            }
                        } else {
                            sb.append(n)
                            i += 2
                        }
                    }
                    'n' -> {
                        sb.append('\n')
                        i += 2
                    }
                    't' -> {
                        sb.append('\t')
                        i += 2
                    }
                    'r' -> {
                        sb.append('\r')
                        i += 2
                    }
                    '"' -> {
                        sb.append('"')
                        i += 2
                    }
                    '\\' -> {
                        sb.append('\\')
                        i += 2
                    }
                    else -> {
                        sb.append(n)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i += 1
            }
        }
        return sb.toString()
    }

    private fun showToast(message: String) {
        val context = Injekt.get<Application>()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun today(): String = LocalDate.now().toString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        EditTextPreference(context).apply {
            key = USERNAME_PREF
            title = "用户名（用于收藏夹）"
            summary = "在浏览本图源时，用应用内置浏览器打开网页并登录账号，登录态会自动同步到插件（无需填写密码）。\n" +
                "留空时插件会尝试从登录状态自动识别用户名；识别失败时请在此手动填写（顶栏账号即用户名）。"
            setDefaultValue("")
        }.let(screen::addPreference)

        SwitchPreferenceCompat(context).apply {
            key = CHECKIN_PREF
            title = "自动签到"
            summary = "每次打开本图源时自动进行每日签到"
            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(context).apply {
            title = "立即签到"
            summary = "点击立即签到一次"
            setOnPreferenceChangeListener { _, _ ->
                Thread {
                    try {
                        val msg = performCheckIn()
                        preferences.edit().putString(CHECKIN_DATE_PREF, today()).apply()
                        showToast("签到成功：$msg")
                    } catch (_: AlreadyCheckedInException) {
                        showToast("今日已签到")
                    } catch (e: Exception) {
                        showToast("签到失败：${e.message}")
                    }
                }.start()
                false
            }
        }.let(screen::addPreference)

        getPreferenceList(context, preferences, updateUrlInterceptor.isUpdated).forEach(screen::addPreference)
        screen.addRandomUAPreference()
    }

    companion object {
        private const val PREFIX_ID_SEARCH_NO_COLON = "JM"
        const val PREFIX_ID_SEARCH = "$PREFIX_ID_SEARCH_NO_COLON:"

        private const val USERNAME_PREF = "username"
        private const val DETECTION_TIMEOUT = 30_000L
        private const val FAVORITE_MANGA_SELECTOR = "div[id^='favorites_album_']"
        private const val CHECKIN_PREF = "auto_checkin"
        private const val CHECKIN_DATE_PREF = "last_checkin_date"
        private const val LOGIN_NOTICE_INTERVAL = 5_000L

        private val USERNAME_EXTRACTION_JS = """
            (function() {
                function extract(scope) {
                    var links = scope.querySelectorAll('a[href*="favorite"]');
                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].getAttribute('href') || '';
                        var parts = href.split('/');
                        var ui = parts.indexOf('user');
                        if (ui >= 0 && parts[ui + 1] && parts[ui + 1].length > 0) return parts[ui + 1];
                    }
                    return null;
                }
                var nav = document.querySelector('#Comic_Top_Nav');
                if (nav) { var u = extract(nav); if (u) return u; }
                return extract(document);
            })()
        """.trimIndent()
    }
}
