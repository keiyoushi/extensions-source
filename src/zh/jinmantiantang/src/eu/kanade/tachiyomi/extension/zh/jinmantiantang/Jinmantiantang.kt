package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Jinmantiantang :
    KeiSource(),
    ConfigurableSource {

    // 首次加载时迁移旧版镜像列表
    private val preferences = getPreferences { preferenceMigration() }

    // baseUrl 跟随"使用镜像网址"设置
    override val baseUrl: String
        get() = "https://" + preferences.mirrorBaseUrl

    private val updateUrlInterceptor = UpdateUrlInterceptor(preferences)

    override fun OkHttpClient.Builder.configureClient() = apply {
        // 拦截器放最前：主站请求失败时自动拉取新镜像列表
        interceptors().add(0, updateUrlInterceptor)
        addInterceptor(ScrambledImageInterceptor)
        // 设置了账号密码时自动登录（cookie 与应用内置浏览器共享）
        addInterceptor(LoginInterceptor(preferences, network.client, { baseUrl }, { headers }))
        // Add rate limit to fix manga thumbnail load failure
        rateLimit(3, 2.seconds) { it.host == baseUrl.toHttpUrl().host }
    }

    // 点击量排序(人气)
    override suspend fun getPopularManga(page: Int): MangasPage {
        maybeAutoCheckIn()
        return mangaListParse(client.get("$baseUrl/albums?o=mv&page=$page"))
    }

    // 最新排序
    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/albums?o=mr&page=$page"))

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.list-col > div.p-b-15:not([data-group])")
            .map { popularMangaFromElement(it) }
            .filterGenre()
        val hasNextPage = document.selectFirst("a.prevnext") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun List<SManga>.filterGenre(): List<SManga> {
        val removedGenres = preferences.blockList
        if (removedGenres.all { it.isBlank() }) return this
        return this.filterNot { manga ->
            manga.genre.orEmpty().lowercase().split(", ").any { it in removedGenres }
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
            author = children[2].select("a").joinToString { it.text() }
            genre = children[3].select("a").joinToString { it.text() }
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (filters.firstInstanceOrNull<FavoritesFilter>()?.isEnabled() == true) {
            return fetchFavorites(page)
        }

        // JM号搜索: JM123 / 123 直接打开对应作品
        if (query.startsWith(PREFIX_ID_SEARCH_NO_COLON, ignoreCase = true) || query.toIntOrNull() != null) {
            val id = query.removePrefix(PREFIX_ID_SEARCH_NO_COLON).removePrefix(":").trim()
            val manga = mangaDetailsParse(mangaDetailsResolve(client.get("$baseUrl/album/$id"))).apply {
                url = "/album/$id/"
            }
            return MangasPage(listOf(manga), hasNextPage = false)
        }

        return mangaListParse(client.get(searchUrl(page, query, filters)))
    }

    // 禁漫天堂特有搜索方式: A +B --> A and B, A B --> A or B
    private fun searchUrl(page: Int, query: String, filters: FilterList): HttpUrl {
        val params = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }

        if (query.isNotEmpty() && !query.contains("-")) {
            var keyword = query
            var rest = params.substringAfter("?")
            if (rest.contains("search_query")) {
                // 分类本身就是一个搜索词, 两个关键词之间用 + 连接表示 AND
                keyword = "$keyword+" + rest.substringBefore("&").substringAfter("=")
                rest = rest.substringAfter("&")
            }
            return buildUrl("/search/photos?$rest", mapOf("search_query" to keyword, "page" to "$page"))
        }

        val path = if (params.isEmpty()) "/albums?" else params
        return if (query.isEmpty()) {
            buildUrl(path, mapOf("page" to "$page"))
        } else {
            // 在搜索栏的关键词前添加-号来实现对筛选结果的过滤, 像 "-YAOI -扶他 -毛絨絨 -獵奇", 注意此时搜索功能不可用.
            val removedGenres = query.split(" ").filter { it.startsWith("-") }.joinToString("+") { it.removePrefix("-") }
            buildUrl(path, mapOf("page" to "$page", "screen" to removedGenres))
        }
    }

    private fun buildUrl(pathAndQuery: String, extra: Map<String, String>): HttpUrl {
        val path = pathAndQuery.substringBefore("?")

        return "$baseUrl$path".toHttpUrl().newBuilder()
            .apply {
                pathAndQuery.substringAfter("?", "")
                    .split("&")
                    .filter { "=" in it }
                    .forEach { addQueryParameter(it.substringBefore("="), it.substringAfter("=")) }
                extra.forEach { (key, value) -> addQueryParameter(key, value) }
            }
            .build()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1) ?: return null
        return mangaDetailsParse(mangaDetailsResolve(client.get(url))).apply {
            this.url = "/album/$id/"
        }
    }

    // 详情与章节来自同一个页面，只请求一次
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = mangaDetailsResolve(client.get("$baseUrl${manga.url}"))

        val updatedManga = mangaDetailsParse(document).apply { url = manga.url }
        if (updatedManga.title.isBlank()) {
            blockInvalidData(document)
        }

        return SMangaUpdate(updatedManga, chapterListParse(document))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = mutableListOf<Page>()
        var document = client.get("$baseUrl${chapter.url}").asJsoup()

        while (true) {
            document.select("div[class=center scramble-page spnotice_chk][id*=0]").forEach { element ->
                val img = element.selectFirst("img") ?: return@forEach
                val src = img.attr("src")
                val dataCfsrc = img.attr("data-cfsrc")
                val imageUrl = if (src.contains("blank.jpg") || dataCfsrc.contains("blank.jpg")) {
                    img.attr("data-original").substringBefore("?")
                } else {
                    src.substringBefore("?")
                }
                pages.add(Page(pages.size, imageUrl = imageUrl))
            }

            val next = document.selectFirst("a.prevnext") ?: break
            document = client.get(next.attr("abs:href")).asJsoup()
        }

        return pages
    }

    // 收藏夹
    private suspend fun fetchFavorites(page: Int): MangasPage {
        val username = getUsername().ifBlank {
            detectUsername().also { detected ->
                if (detected.isNotBlank()) {
                    preferences.edit().putString(USERNAME_PREF, detected).apply()
                }
            }
        }

        if (username.isBlank()) {
            throw Exception("未检测到登录状态：请在应用内置浏览器中打开网页并登录，或在插件设置中手动填写用户名")
        }

        val document = client.get("$baseUrl/user/$username/favorite/albums?page=$page").asJsoup()
        val mangas = document.select(FAVORITE_MANGA_SELECTOR).map { favoriteMangaFromElement(it) }

        if (mangas.isEmpty() && !document.isLoggedIn()) {
            throw Exception("登录已过期，请重新在应用内置浏览器中登录")
        }

        val hasNextPage = document.selectFirst("a.prevnext") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun favoriteMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a[href*='/album/']")
        if (link != null) {
            setUrlWithoutDomain(link.attr("href").substringBefore("?"))
            title = element.selectFirst(".video-title")?.text() ?: "Unknown"
            val img = element.selectFirst(".thumb-overlay img")
            thumbnail_url = img?.extractThumbnailUrl()?.substringBeforeLast('?').orEmpty()
        } else {
            title = "Unknown"
        }
    }

    // 登录与守卫
    private fun getUsername(): String = preferences.getString(USERNAME_PREF, "")?.trim() ?: ""

    private fun Document.isLoggedIn(): Boolean = selectFirst("#Comic_Top_Nav")?.selectFirst("a[href*='favorite'], a[href*='logout']") != null

    private suspend fun detectUsername(): String {
        val nav = client.get(baseUrl).asJsoup().selectFirst("#Comic_Top_Nav") ?: return ""
        return nav.select("a[href*='favorite']")
            .map { it.attr("href").substringAfter("/user/", "").substringBefore("/") }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun blockInvalidData(document: Document): Nothing = throw Exception(
        if (document.isLoggedIn()) {
            "无法获取漫画数据，已阻断请求以保护书架"
        } else {
            "未登录，该内容无法加载，已阻断请求"
        },
    )

    // 漫画详情
    private fun mangaDetailsResolve(response: Response): Document {
        val document = response.asJsoup()
        val scripts =
            document.select("#wrapper > script:containsData(function base64DecodeUtf8):containsData(document.write(html))")

        for (script in scripts) {
            script.html().trim().lines().forEach { line ->
                val trimmedLine = line.trim()
                // html = base64DecodeUtf8("...")
                if (trimmedLine.startsWith("const html") || trimmedLine.startsWith("let html") ||
                    trimmedLine.startsWith("var html")
                ) {
                    val start = trimmedLine.indexOf("base64DecodeUtf8(\"") + "base64DecodeUtf8(\"".length
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

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    private fun Element.extractThumbnailUrl(): String = when {
        hasAttr("data-original") -> absUrl("data-original")
        hasAttr("src") -> absUrl("src")
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
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

    private fun chapterListParse(document: Document): List<SChapter> {
        val elements = document.select("div[id=episode-block] a[href^=/photo/]")
        if (elements.isEmpty()) {
            val singleUrl = document.selectFirst("#album_photo_cover > div.thumb-overlay > a")?.attr("href")
            if (singleUrl.isNullOrBlank()) {
                blockInvalidData(document)
            }
            val singleChapter = SChapter.create().apply {
                name = "单章节"
                url = singleUrl
                date_upload = dateFormat.tryParse(document.select("[itemprop=datePublished]").last()?.attr("content"))
            }
            return listOf(singleChapter)
        }
        return elements.map { chapterFromElement(it) }.reversed()
    }

    // Filters
    override fun getFilterList(data: JsonElement?) = FilterList(
        FavoritesFilter(),
        Filter.Separator(),
        CategoryGroup(),
        SortFilter(),
        TimeFilter(),
        TypeFilter(),
    )

    // 签到
    private class AlreadyCheckedInException : Exception()

    private fun maybeAutoCheckIn() {
        if (!preferences.getBoolean(CHECKIN_PREF, false)) return
        if (preferences.getString(CHECKIN_DATE_PREF, "") == today()) return

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val msg = performCheckIn()
                preferences.edit().putString(CHECKIN_DATE_PREF, today()).apply()
                showToast("签到成功：$msg")
            } catch (_: AlreadyCheckedInException) {
                preferences.edit().putString(CHECKIN_DATE_PREF, today()).apply()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun performCheckIn(): String {
        val homepage = client.get("$baseUrl/").asJsoup()
        if (!homepage.isLoggedIn()) {
            throw Exception("登录已过期，请重新在应用内置浏览器中登录")
        }

        val dailyId = homepage.selectFirst("#bouns-popup")?.attr("data-dailyid")
            ?: throw Exception("未找到签到入口")

        val eventJson = client.get("$baseUrl/ajax/user_daily_event?daily_id=$dailyId").body.string()
        val oldStep = Regex("\"oldStep\":(\\d+)").find(eventJson)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val body = FormBody.Builder()
            .add("daily_id", dailyId)
            .add("oldStep", (oldStep + 1).toString())
            .build()

        val signJson = client.post("$baseUrl/ajax/user_daily_sign", body = body).body.string()

        if (extractJsonString(signJson, "error") == "finished") {
            throw AlreadyCheckedInException()
        }

        val msg = extractJsonString(signJson, "msg")
        if (msg != null && ("已經完成每日簽到" in msg || "已经完成每日签到" in msg)) {
            return msg
        }
        throw Exception(msg ?: "签到失败，请检查是否已登录")
    }

    private fun extractJsonString(json: String, field: String): String? {
        val match = Regex("\"$field\":\"((?:\\\\.|[^\"\\\\])*)\"").find(json) ?: return null
        return unescapeJson(match.groupValues[1])
    }

    private fun unescapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'u' -> {
                        val code = if (i + 5 < value.length) value.substring(i + 2, i + 6).toIntOrNull(16) else null
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                        } else {
                            sb.append("\\u")
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
                        sb.append(next)
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
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun today(): String = LocalDate.now().toString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        EditTextPreference(context).apply {
            key = USERNAME_PREF
            title = "用户名（登录账号 / 收藏夹）"
            summary = "填写用户名并设置密码后，插件会自动登录，无需再打开内置浏览器手动登录。\n" +
                "只在网页登录的用户可不填密码：用户名留空时插件会自动识别登录状态用于收藏夹；识别失败请在此手动填写（顶栏账号即用户名）。"
            setDefaultValue("")
            // 修改账号时清除会话 cookie，让新凭据立即生效
            setOnPreferenceChangeListener { _, _ ->
                clearSessionCookies(baseUrl)
                true
            }
        }.let(screen::addPreference)

        // 密码设置项（与用户名一起构成自动登录凭据）
        EditTextPreference(context).apply {
            key = PASSWORD_PREF
            title = "密码"
            summary = if (preferences.getString(key, "").isNullOrEmpty()) "未设置，部分漫画需要登录才能观看" else "已设置"
            dialogTitle = title
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { pref, newValue ->
                (pref as EditTextPreference).summary =
                    if ((newValue as String).isEmpty()) "未设置，部分漫画需要登录才能观看" else "已设置"
                clearSessionCookies(baseUrl)
                true
            }
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
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val msg = performCheckIn()
                        preferences.edit().putString(CHECKIN_DATE_PREF, today()).apply()
                        showToast("签到成功：$msg")
                    } catch (_: AlreadyCheckedInException) {
                        showToast("今日已签到")
                    } catch (e: Exception) {
                        showToast("签到失败：${e.message}")
                    }
                }
                false
            }
        }.let(screen::addPreference)

        // 含"使用镜像网址"设置项
        getPreferenceList(context, preferences, updateUrlInterceptor.isUpdated).forEach(screen::addPreference)
    }

    companion object {
        private const val PREFIX_ID_SEARCH_NO_COLON = "JM"

        private const val FAVORITE_MANGA_SELECTOR = "div[id^='favorites_album_']"
        private const val CHECKIN_PREF = "auto_checkin"
        private const val CHECKIN_DATE_PREF = "last_checkin_date"
    }
}
