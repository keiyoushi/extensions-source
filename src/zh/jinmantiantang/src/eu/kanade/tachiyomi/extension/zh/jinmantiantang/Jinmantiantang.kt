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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.selectLast
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Jinmantiantang :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences { preferenceMigration() }

    override val baseUrl: String
        get() = "https://" + preferences.mirrorBaseUrl

    private val updateUrlInterceptor = UpdateUrlInterceptor(preferences)

    override fun OkHttpClient.Builder.configureClient() = apply {
        // 拦截器放最前：主站请求失败时自动拉取新镜像列表
        interceptors().add(0, updateUrlInterceptor)
        addInterceptor(ScrambledImageInterceptor)
        addInterceptor(LoginInterceptor(preferences, network.client, { baseUrl }, { headers }))
        // Add rate limit to fix manga thumbnail load failure
        rateLimit(3, 2.seconds) { it.host == baseUrl.toHttpUrl().host }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        maybeAutoCheckIn()
        return mangaListParse(client.get(albumsUrl("mv", page)))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get(albumsUrl("mr", page)))

    private fun albumsUrl(order: String, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("albums")
        .addQueryParameter("o", order)
        .addQueryParameter("page", page.toString())
        .build()

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
            children[0].selectFirst("a")?.let { url = albumUrl(it.absUrl("href"))!! }
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
        val hasIdPrefix = query.startsWith(PREFIX_ID_SEARCH_NO_COLON, ignoreCase = true)
        if (hasIdPrefix || (query.isNotEmpty() && query.all(Char::isDigit))) {
            val id = (if (hasIdPrefix) query.substring(PREFIX_ID_SEARCH_NO_COLON.length) else query).removePrefix(":").trim()
            return MangasPage(listOfNotNull(fetchAlbum("$baseUrl/album/$id")), hasNextPage = false)
        }

        return mangaListParse(client.get(searchUrl(page, query, filters)))
    }

    // The site's search: "A +B" is A and B, "A B" is A or B, "-A" leaves out A
    private fun searchUrl(page: Int, query: String, filters: FilterList): HttpUrl {
        val keywords = listOfNotNull(query.takeIf { it.isNotBlank() }) +
            filters.filterIsInstance<TagFilter>().map { it.value }.filter { it.isNotEmpty() }

        return baseUrl.toHttpUrl().newBuilder().apply {
            if (keywords.isNotEmpty()) {
                addPathSegments("search/photos")
                addQueryParameter("search_query", keywords.joinToString(" +"))
                addQueryParameter("main_tag", filters.firstInstance<TypeFilter>().value)
            } else {
                addPathSegment("albums")
                filters.firstInstance<CategoryFilter>().value.takeIf { it.isNotEmpty() }?.let(::addPathSegments)
            }
            addQueryParameter("o", filters.firstInstance<SortFilter>().value)
            addQueryParameter("t", filters.firstInstance<TimeFilter>().value)
            addQueryParameter("page", page.toString())
        }.build()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "album") return null
        return fetchAlbum(url.toString())
    }

    // /album/<chapter id> redirects to its album; the canonical link names it
    private suspend fun fetchAlbum(url: String): SManga? {
        val document = mangaDetailsResolve(client.get(url))
        val albumUrl = albumUrl((document.selectFirst("link[rel=canonical]") ?: blockInvalidData(document)).absUrl("href")) ?: return null
        return mangaDetailsParse(document).apply { this.url = albumUrl }
    }

    // 详情与章节来自同一个页面，只请求一次
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = mangaDetailsResolve(client.get(getMangaUrl(manga)))

        val updatedManga = mangaDetailsParse(document).apply { url = manga.url }

        return SMangaUpdate(updatedManga, chapterListParse(document))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = mutableListOf<Page>()
        var document = client.get(getChapterUrl(chapter)).asJsoup()

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

        val favoritesUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("user")
            .addPathSegment(username)
            .addPathSegments("favorite/albums")
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(favoritesUrl).asJsoup()
        val mangas = document.select(FAVORITE_MANGA_SELECTOR).map { favoriteMangaFromElement(it) }

        if (mangas.isEmpty() && !document.isLoggedIn()) {
            throw Exception("登录已过期，请重新在应用内置浏览器中登录")
        }

        val hasNextPage = document.selectFirst("a.prevnext") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun favoriteMangaFromElement(element: Element): SManga = SManga.create().apply {
        url = albumUrl(element.selectFirst("a[href*='/album/']")!!.absUrl("href"))!!
        title = element.selectFirst(".video-title")!!.text()
        thumbnail_url = element.selectFirst(".thumb-overlay img")?.extractThumbnailUrl()?.substringBeforeLast('?')
    }

    private fun getUsername(): String = preferences.getString(USERNAME_PREF, "")?.trim() ?: ""

    private fun Document.isLoggedIn(): Boolean = selectFirst("#Comic_Top_Nav")?.selectFirst("a[href*='favorite'], a[href*='logout']") != null

    private suspend fun detectUsername(): String {
        val nav = client.get(baseUrl).asJsoup().selectFirst("#Comic_Top_Nav") ?: return ""
        return nav.select("a[href*='/user/']")
            .firstNotNullOfOrNull { link ->
                link.absUrl("href").toHttpUrlOrNull()?.pathSegments
                    ?.takeIf { it.getOrNull(0) == "user" }
                    ?.getOrNull(1)
            }
            .orEmpty()
    }

    private fun blockInvalidData(document: Document): Nothing = throw Exception(
        if (document.isLoggedIn()) {
            "无法获取漫画数据，已阻断请求以保护书架"
        } else {
            "未登录，该内容无法加载，已阻断请求"
        },
    )

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
        title = document.selectFirst("h1")?.textOrNull() ?: blockInvalidData(document)
        // keep thumbnail_url same as the one in popularMangaFromElement()
        val img = document.selectFirst(".thumb-overlay > img")
        thumbnail_url = img?.extractThumbnailUrl()?.substringBeforeLast('.') + "_3x4.jpg"
        author = document.select("div.panel-body div.tag-block").getOrNull(3)?.select(".btn-primary")?.joinToString { it.text() }?.ifEmpty { null }
        val tags = document.selectFirst("span[itemprop=genre]")?.select("a")?.map { it.text() }.orEmpty()
        genre = tags.filterNot { it == "連載中" || it == "完結" }.joinToString().ifEmpty { null }
        status = when {
            "連載中" in tags -> SManga.ONGOING
            "完結" in tags -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = document.selectFirst("#intro-block .p-t-5.p-b-5")?.text()?.substringAfter("敘述：")?.trim()?.ifEmpty { null }
    }

    private fun albumUrl(url: String): String? {
        val segments = url.toHttpUrl().pathSegments
        return if (segments[0] == "album") "/album/${segments[1]}/" else null
    }

    private fun Element.extractThumbnailUrl(): String = when {
        hasAttr("data-original") -> absUrl("data-original")
        hasAttr("src") -> absUrl("src")
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        else -> ""
    }

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("Asia/Taipei"))

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.attr("href")
        name = element.selectFirst("li h3")!!.ownText()
        date_upload = dateFormat.tryParseDate(element.selectFirst("li span.hidden-xs")?.text())
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
                date_upload = dateFormat.tryParseDate(document.selectLast("[itemprop=datePublished]")?.attr("content"))
            }
            return listOf(singleChapter)
        }
        return elements.map { chapterFromElement(it) }.reversed()
    }

    override val supportsFilterFetching get() = true

    // Tag groups from the site's tag page, without its trending searches
    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/theme/").asJsoup()
        .select("div.row:has(> h4)")
        .map { row ->
            row.selectFirst("h4")!!.text() to row.select("a[href*=search_query]")
                .map { it.absUrl("href").toHttpUrl().queryParameter("search_query")!! }
                .distinct()
        }
        .filter { (name, tags) -> name != TRENDING_TAGS && tags.isNotEmpty() }
        .ifEmpty { throw Exception("No tags on the tag page") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tagGroups = data?.parseAs<List<Pair<String, List<String>>>>().orEmpty()
        return FilterList(
            FavoritesFilter(),
            Filter.Separator(),
            Filter.Header("搜索或选择标签时不按类型筛选"),
            CategoryFilter(),
            *tagGroups.map { (name, tags) -> TagFilter(name, tags) }.toTypedArray(),
            SortFilter(),
            TimeFilter(),
            TypeFilter(),
        )
    }

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

        val eventUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("ajax/user_daily_event")
            .addQueryParameter("daily_id", dailyId)
            .build()
        val eventJson = client.get(eventUrl).body.string()
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
        }.let(screen::addPreference)

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

        getPreferenceList(context, preferences, updateUrlInterceptor.isUpdated).forEach(screen::addPreference)
    }

    companion object {
        private const val PREFIX_ID_SEARCH_NO_COLON = "JM"
        private const val TRENDING_TAGS = "熱門成人主題"

        private const val FAVORITE_MANGA_SELECTOR = "div[id^='favorites_album_']"
        private const val CHECKIN_PREF = "auto_checkin"
        private const val CHECKIN_DATE_PREF = "last_checkin_date"
    }
}
