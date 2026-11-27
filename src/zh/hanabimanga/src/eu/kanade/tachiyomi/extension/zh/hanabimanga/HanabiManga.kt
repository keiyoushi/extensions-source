package eu.kanade.tachiyomi.extension.zh.hanabimanga

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.applicationContext
import keiyoushi.utils.decodeHex
import keiyoushi.utils.getLong
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.Locale

private const val ANONYMOUS_TOKEN =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVoa3ZxcnhtY2FwZ3Rwc3BnbHJwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM5NjgzMjksImV4cCI6MjA3OTU0NDMyOX0.uuHr888lp14ObW5eWowJrHPJGgQf3sF2l7NPmFN84g4"
private const val COMIC_BODY =
    "id,title,summary,cover_url,release_date,is_finished,authors,region,latest_chapter_title,tags(id,name),categories(id,name)"
private const val PAGE_SIZE = 20
private const val MAX_PAGES_PER_REQUEST = 30
private const val REFRESH_TOKEN_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val KEY_ID = "android-2050499-release-1"
private const val NATIVE_SECRET = "8c7856eb875e0a5150f61bda309f21c78c855590b132186a2216dda5d2949727"
private const val CERT_FINGERPRINT = "13a8c9fbdaf19115f39b48dc0f3a7c99568ebb0c91ba1a71218c76f53f7877a8"

@Source
abstract class HanabiManga :
    KeiSource(),
    ConfigurableSource {
    override fun getHomeUrl() = "https://web.hanabimanga.com/zh-CN"

    override fun Headers.Builder.configureHeaders() = apply {
        add("apikey", ANONYMOUS_TOKEN)
        removeAll("Referer")
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(TileScrambleInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request).also {
                if (it.code == 401 && request.url.queryParameterNames.containsAll(listOf("t", "sign"))) {
                    it.use { throw IOException("图片链接已过期，请清除章节缓存后重试") }
                }
            }
        }
    }

    private val pref by getPreferencesLazy()

    private var loginPref: Preference? = null

    @Volatile private var loginError: String? = null

    @Volatile private var loginPending = false

    @Volatile private var signedClientDisabled = false

    private val signingKey by lazy {
        "xfmanga/reader/v2|$CERT_FINGERPRINT".hmacSha256(NATIVE_SECRET.decodeHex())
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(
            screen.context,
            pref,
            ::onCredentialsChanged,
            ::currentLoginStatus,
            { loginError },
        ).forEach { p ->
            if (p.key == PREF_EMAIL) loginPref = p
            screen.addPreference(p)
        }
    }

    // Customize

    private suspend fun token(): String {
        val httpUrl = getHomeUrl().toHttpUrl()
        val access = client.cookieJar.loadForRequest(httpUrl).find { it.name == "access_token" }
        if (access != null) return access.value

        val refresh = client.cookieJar.loadForRequest(httpUrl).find { it.name == "refresh_token" }
        if (refresh == null) return login()

        val refreshBody = buildJsonObject { put("refresh_token", refresh.value) }
        val response = client.post("$baseUrl/auth/v1/token?grant_type=refresh_token", Fetch.XHR, refreshBody.toJsonRequestBody(), false)

        return if (response.isSuccessful) {
            response.parseAs<JsonObject>().also(::saveTokens).getString("access_token")
        } else {
            login()
        }
    }

    private suspend fun login(): String {
        val email = pref.getString(PREF_EMAIL, null)?.trim()?.takeIf(String::isNotEmpty)
        val password = pref.getString(PREF_PASSWORD, null)?.takeIf(String::isNotEmpty)
        if (email == null || password == null) return ANONYMOUS_TOKEN

        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            putJsonObject("gotrue_meta_security") {}
        }
        val response = client.post("$baseUrl/auth/v1/token?grant_type=password", Fetch.XHR, body.toJsonRequestBody(), false)
        if (response.code == 400) throw Exception("登录失败，邮箱或密码错误")

        return response.parseAs<JsonObject>().also(::saveTokens).getString("access_token").also { loginError = null }
    }

    private fun saveTokens(json: JsonObject) {
        val httpUrl = getHomeUrl().toHttpUrl()
        val expiresAt = json.getLong("expires_at") * 1000L
        val c1 = Cookie.Builder().name("access_token").value(json.getString("access_token")).domain(httpUrl.host)
            .expiresAt(expiresAt).build()
        val c2 = Cookie.Builder().name("refresh_token").value(json.getString("refresh_token")).domain(httpUrl.host)
            .expiresAt(expiresAt + REFRESH_TOKEN_TTL_MS).build()
        client.cookieJar.saveFromResponse(httpUrl, listOf(c1, c2))
    }

    private fun clearTokens() {
        val httpUrl = getHomeUrl().toHttpUrl()
        val expired = listOf("access_token", "refresh_token").map {
            Cookie.Builder().name(it).value("").domain(httpUrl.host).expiresAt(0).build()
        }
        client.cookieJar.saveFromResponse(httpUrl, expired)
    }

    private fun hasSession() = client.cookieJar.loadForRequest(getHomeUrl().toHttpUrl()).any { it.name == "access_token" }

    private fun currentLoginStatus() = when {
        loginPending -> LoginStatus.PENDING
        loginError != null -> LoginStatus.FAILED
        hasCredentials() || hasSession() -> LoginStatus.ACTIVE
        else -> LoginStatus.NONE
    }

    private fun hasCredentials() = !pref.getString(PREF_EMAIL, null).isNullOrBlank() &&
        !pref.getString(PREF_PASSWORD, null).isNullOrBlank()

    /** A credential edit takes effect at once: the old session goes and a complete new pair logs in right away. */
    private fun onCredentialsChanged() {
        clearTokens()
        loginError = null
        if (!hasCredentials()) {
            toast("已退出登录")
            refreshLoginSummary()
            return
        }
        loginPending = true
        refreshLoginSummary()
        CoroutineScope(Dispatchers.IO).launch {
            val error = runCatching { login() }.exceptionOrNull()
            loginError = error?.let { it.message?.takeIf(String::isNotBlank) ?: "网络或服务异常" }
            loginPending = false
            toast(if (error == null) "登录成功" else "登录失败：$loginError")
            refreshLoginSummary()
        }
    }

    private fun refreshLoginSummary() = Handler(Looper.getMainLooper()).post {
        loginPref?.summary = loginSummary(pref, currentLoginStatus(), loginError)
    }

    private fun toast(text: String) = Handler(Looper.getMainLooper()).post {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    private fun HttpUrl.Builder.addPaginationParameters(page: Int, order: String) = addQueryParameter("select", COMIC_BODY)
        .addQueryParameter("order", order)
        .addQueryParameter("offset", "${(page - 1) * PAGE_SIZE}")
        .addQueryParameter("limit", PAGE_SIZE.toString())

    private fun HttpUrl.Builder.addSelectParameter(fetchDetails: Boolean, fetchChapters: Boolean) = addQueryParameter(
        "select",
        buildString {
            if (fetchDetails) append(COMIC_BODY)
            if (fetchDetails && fetchChapters) append(',')
            if (fetchChapters) append("chapters(id,idx,title,image_count,category,updated_at)")
        },
    )

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val order = pref.getString(PREF_POPULAR_MANGA, "popularity_daily.desc.nullslast")!!
        val url = "$baseUrl/rest/v1/comics".toHttpUrl().newBuilder().addPaginationParameters(page, order).build()
        val comics = client.get(url, Fetch.XHR).parseAs<List<Comic>>()
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Update

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/rest/v1/comics".toHttpUrl().newBuilder().addPaginationParameters(page, "updated_at.desc.nullslast").build()
        val comics = client.get(url, Fetch.XHR).parseAs<List<Comic>>()
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Search

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val comics = if (query.isNotBlank()) {
            val body = mapOf("search_term" to query, "items_per_page" to "$PAGE_SIZE", "page_number" to "$page")
            client.post("$baseUrl/rest/v1/rpc/search_comics_pgroonga", Fetch.XHR, body.toJsonRequestBody()).parseAs<List<Comic>>()
        } else {
            val url = "$baseUrl/rest/v1/comics".toHttpUrl().newBuilder().apply {
                addPaginationParameters(page, filters[2].toString())
                if (filters[1].toString().isNotBlank()) addQueryParameter("category_id", "eq.${filters[1]}")
                if (filters[4].toString().isNotBlank()) addQueryParameter("is_finished", "eq.${filters[4]}")
                if (filters[3].toString().isNotBlank()) addQueryParameter("region", "eq.${filters[3]}")
            }
            client.get(url.build(), Fetch.XHR).parseAs<List<Comic>>()
        }
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Manga & Chapter

    override fun getMangaUrl(manga: SManga) = "${getHomeUrl()}/comic/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "${getHomeUrl()}/comic/${chapter.memo["cid"]!!.int}/chapter-${chapter.memo["idx"]!!.int}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$baseUrl/rest/v1/comics".toHttpUrl().newBuilder().addQueryParameter("id", "eq.${manga.url}")
            .addSelectParameter(fetchDetails, fetchChapters).build()
        val response = client.get(url, Fetch.XHR)
        val comic = response.parseAs<List<Comic>>().first()

        val smanga = if (fetchDetails) comic.toSManga() else manga
        val schapters = if (fetchChapters) {
            comic.chapters!!.sortedWith(compareBy<Chapter> { if (it.category == "volume") 1 else 0 }.thenByDescending { it.idx })
                .map { it.toSChapter(manga.url) }
        } else {
            chapters
        }
        return SMangaUpdate(smanga, schapters)
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val body = buildJsonObject {
            put("p_limit", 9)
            put("p_category_id", manga.memo["category_id"]!!.int)
            put("p_exclude_comic_id", manga.url)
        }
        val comics = client.post("$baseUrl/rest/v1/rpc/get_random_comics", Fetch.XHR, body.toJsonRequestBody()).parseAs<List<Comic>>()
        return comics.map(Comic::toSManga)
    }

    // Page

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pageCount = chapter.memo["size"]!!.int
        if (pageCount == 0) return emptyList()

        val comicId = chapter.memo["cid"]!!.int
        val ep = if (pref.getBoolean(PREF_AI_SR, false)) "hd" else "sd"
        val pages = List(pageCount) { "%03d".format(Locale.ROOT, it + 1) }

        if (!signedClientDisabled) {
            try {
                return getSignedPageList(comicId, chapter.url, pages, ep)
            } catch (_: ClientSignatureRejectedException) {
                signedClientDisabled = true
            }
        }
        return getWebPageList(comicId, chapter.url, pages, ep)
    }

    private suspend fun getSignedPageList(comicId: Int, chapterId: String, pages: List<String>, ep: String): List<Page> {
        val timestamp = System.currentTimeMillis() / 1000
        val canonical = "v2|scope=reader|ep=$ep|key_id=$KEY_ID|comic_id=$comicId|chapter_id=$chapterId|pages=${pages.joinToString(",")}|timestamp=$timestamp"
        val body = buildJsonObject {
            put("comic_id", comicId)
            put("chapter_id", chapterId)
            putJsonArray("pages") { addAll(pages) }
            put("timestamp", timestamp)
            put("signature", canonical.hmacSha256(signingKey).toHexString())
            put("key_id", KEY_ID)
        }
        val response = postImageUrl(ep, body)
        if (response.code == 401) response.use { throw Exception("请先在插件设置中登录") }
        if (!response.isSuccessful) {
            val error = parseError(response)
            if (error.code == "CLIENT_SIGNATURE_RETIRED" || error.code == "CLIENT_VERIFY_FAILED") {
                throw ClientSignatureRejectedException()
            }
            throw Exception(error.message)
        }
        return response.parseAs<PagesResult>().toPages(0)
    }

    private suspend fun getWebPageList(comicId: Int, chapterId: String, pages: List<String>, ep: String): List<Page> = pages.chunked(MAX_PAGES_PER_REQUEST).flatMap { chunk ->
        val body = buildJsonObject {
            put("comic_id", comicId)
            put("chapter_id", chapterId)
            putJsonArray("pages") { addAll(chunk) }
        }
        val response = postImageUrl(ep, body)
        if (response.code == 401) response.use { throw Exception("请先在插件设置中登录") }
        if (!response.isSuccessful) throw Exception(parseError(response).message)
        response.parseAs<PagesResult>().toPages(chunk.first().toInt() - 1)
    }

    private suspend fun postImageUrl(ep: String, body: JsonObject): Response = client.post(
        "$baseUrl/functions/v1/$ep-image-url",
        headers.newBuilder().add("Authorization", "Bearer ${token()}").build(),
        Fetch.XHR,
        body.toJsonRequestBody(),
        false,
    )

    private fun PagesResult.toPages(offset: Int): List<Page> {
        val fragment = scrambleInfo?.let { "#${it.ticket}|${it.nonce}|${it.cols}|${it.rows}" }.orEmpty()
        return urls.mapIndexed { i, p -> Page(offset + i, imageUrl = p.getString("url") + fragment) }
    }

    private fun parseError(response: Response): ErrorInfo {
        val text = response.use { it.body.string() }
        val json = runCatching { text.parseAs<JsonObject>() }.getOrNull()
        val code = json?.getStringOrNull("code")
        val message = when (code) {
            "ANON_QUOTA_EXCEEDED" -> "请先在插件设置中登录"
            "FREE_QUOTA_EXCEEDED" -> "今日超分额度已用完"
            "TOO_MANY_PAGES" -> "单次请求页数超出限制"
            else -> json?.getStringOrNull("message")
                ?: json?.getStringOrNull("error")
                ?: "请求失败 (HTTP ${response.code})"
        }
        return ErrorInfo(code, message)
    }

    private class ErrorInfo(val code: String?, val message: String)

    private class ClientSignatureRejectedException : Exception()
}
