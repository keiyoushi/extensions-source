package eu.kanade.tachiyomi.extension.zh.zaimanhua

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class Zaimanhua : HttpSource(), ConfigurableSource {
    override val lang = "zh"
    override val supportsLatest = true

    override val name = "再漫画"
    override val baseUrl = "https://manhua.zaimanhua.com"
    private val mobileBaseUrl = "https://m.zaimanhua.com"
    private val apiUrl = "https://v4api.zaimanhua.com/app/v1"
    private val accountApiUrl = "https://account-api.zaimanhua.com/v1"
    private val checkTokenRegex = Regex("""$apiUrl/comic/chapter""")

    private val json by injectLazy<Json>()

    private val preferences: SharedPreferences = getPreferences()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .addInterceptor(::authIntercept)
        .addInterceptor(::imageRetryInterceptor)
        .addInterceptor(CommentsInterceptor)
        .build()

    private fun authIntercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val username = preferences.getString(USERNAME_PREF, "")!!
        val password = preferences.getString(PASSWORD_PREF, "")!!
        var token = preferences.getString(TOKEN_PREF, "")!!
        var hasTriedLogin = false

        if (request.url.toString().startsWith(apiUrl) && request.header("authorization") == null && username.isNotBlank() && password.isNotBlank()) {
            token = getToken(username, password)
            apiHeaders = apiHeaders.newBuilder().setToken(token).build()
            hasTriedLogin = true
            preferences.edit().apply {
                if (token.isBlank()) {
                    putString(TOKEN_PREF, "")
                    putString(USERNAME_PREF, "")
                    putString(PASSWORD_PREF, "").apply()
                } else {
                    putString(TOKEN_PREF, token).apply()
                    request = request.newBuilder().headers(apiHeaders).build()
                }
            }
        }
        val response = chain.proceed(request)
        // Only intercept chapter api requests that need token
        if (!request.url.toString().contains(checkTokenRegex)) return response

        // If chapter can read, return directly
        val canRead = response.peekBody(Long.MAX_VALUE).stringCompat(response.header("Content-Encoding"))
            .parseAs<ResponseDto<DataWrapperDto<CanReadDto>>>().data.data?.canRead != false
        if (canRead) return response

        if (!isValid(token) && !hasTriedLogin) {
            token = getToken(username, password)
            apiHeaders = apiHeaders.newBuilder().setToken(token).build()
            preferences.edit().apply {
                if (token.isBlank()) {
                    putString(TOKEN_PREF, "")
                    putString(USERNAME_PREF, "")
                    putString(PASSWORD_PREF, "")
                } else {
                    putString(TOKEN_PREF, token)
                }
            }.apply()
            if (token.isBlank()) return response
        } else if (request.header("authorization") == "Bearer $token") {
            return response
        }

        response.close()
        val authRequest = request.newBuilder().apply {
            header("authorization", "Bearer $token")
            cacheControl(CacheControl.FORCE_NETWORK)
        }.build()
        return chain.proceed(authRequest)
    }

    private fun Headers.Builder.setToken(token: String): Headers.Builder = apply {
        if (token.isNotBlank()) set("authorization", "Bearer $token")
    }

    private var apiHeaders = headersBuilder().setToken(preferences.getString(TOKEN_PREF, "")!!).build()

    private fun isValid(token: String): Boolean {
        if (token.isBlank()) return false
        val parts = token.split(".")
        if (parts.size != 3) throw Exception("token格式错误，不符合JWT规范")
        val payload = Base64.decode(parts[1], Base64.DEFAULT).toString(Charsets.UTF_8).parseAs<JwtPayload>()
        if (payload.expirationTime * 1000 < System.currentTimeMillis()) return false

        val response = client.newCall(
            GET(
                "$accountApiUrl/userInfo/get",
                headersBuilder().setToken(token).build(),
            ),
        ).execute().parseAs<SimpleResponseDto>()
        return response.errno == 0
    }

    private fun getToken(username: String, password: String): String {
        if (username.isBlank() || password.isBlank()) return ""
        val passwordEncoded =
            MessageDigest.getInstance("MD5").digest(password.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        val formBody: RequestBody = FormBody.Builder().addEncoded("username", username)
            .addEncoded("passwd", passwordEncoded).build()
        val response = client.newCall(
            POST(
                "$accountApiUrl/login/passwd",
                headers,
                formBody,
            ),
        ).execute().parseAs<ResponseDto<UserDto>>()
        return response.data.user?.token ?: ""
    }

    // Detail
    override fun getMangaUrl(manga: SManga): String {
        return "$mobileBaseUrl/pages/comic/detail?id=${manga.url}"
    }

    // path: "/comic/detail/mangaId"
    private fun getMangaUrl(id: String): HttpUrl = "$apiUrl/comic/detail/$id?_v=2.2.5".toHttpUrl()
    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(getMangaUrl(manga.url), apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ResponseDto<DataWrapperDto<MangaDto>>>()
        if (result.errmsg.isNotBlank()) {
            throw Exception(result.errmsg)
        } else {
            return result.data.data!!.toSManga()
        }
    }

    // Chapter
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ResponseDto<DataWrapperDto<MangaDto>>>()
        if (result.errmsg.isNotBlank()) {
            throw Exception(result.errmsg)
        } else {
            return result.data.data!!.parseChapterList()
        }
    }

    // PageList
    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, chapterId) = chapter.url.split("/", limit = 2)
        return "$mobileBaseUrl/pages/comic/page?comic_id=$mangaId&chapter_id=$chapterId"
    }

    // path: "/comic/chapter/mangaId/chapterId"
    private fun pageListApiRequest(path: String): Request =
        GET("$apiUrl/comic/chapter/$path?_v=2.2.5", apiHeaders, USE_CACHE)

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val response = client.newCall(pageListApiRequest(chapter.url)).execute()
        val result = response.parseAs<ResponseDto<DataWrapperDto<ChapterImagesDto>>>()
        if (result.errmsg.isNotBlank()) {
            throw Exception(result.errmsg)
        } else {
            if (!result.data.data!!.canRead) {
                throw Exception("用户权限不足，请提升用户等级")
            }
            return Observable.fromCallable {
                val images = result.data.data.images
                val pageList = images.mapIndexedTo(ArrayList(images.size + 1)) { index, it ->
                    val fragment = json.encodeToString(ImageRetryParamsDto(chapter.url, index))
                    Page(index, imageUrl = "$it#$fragment")
                }
                if (preferences.getBoolean(COMMENTS_PREF, false)) {
                    val (mangaId, chapterId) = chapter.url.split("/", limit = 2)
                    pageList.add(Page(pageList.size, COMMENTS_FLAG, chapterCommentsUrl(mangaId, chapterId)))
                }
                pageList
            }
        }
    }

    private fun imageRetryInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment
        if (response.isSuccessful || request.tag(String::class) != IMAGE_RETRY_FLAG || fragment == null) return response
        response.close()

        val params = json.decodeFromString<ImageRetryParamsDto>(fragment)
        val pageListResponse = client.newCall(pageListApiRequest(params.url)).execute()
        val result = pageListResponse.parseAs<ResponseDto<DataWrapperDto<ChapterImagesDto>>>()
        if (result.errmsg.isNotBlank()) {
            throw IOException(result.errmsg)
        } else {
            val imageUrl = result.data.data!!.images[params.index]
            return chain.proceed(GET(imageUrl, headers))
        }
    }

    override fun imageRequest(page: Page): Request {
        val flag = if (page.url == COMMENTS_FLAG) COMMENTS_FLAG else IMAGE_RETRY_FLAG
        val reqHeaders = if (page.url == COMMENTS_FLAG) apiHeaders else headers
        return GET(page.imageUrl!!, reqHeaders).newBuilder()
            .tag(String::class, flag)
            .build()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Popular
    private fun rankApiUrl(): HttpUrl.Builder =
        "$apiUrl/comic/rank/list".toHttpUrl().newBuilder()
            .addQueryParameter("tag_id", "0")

    override fun popularMangaRequest(page: Int): Request = GET(
        rankApiUrl().apply {
            addQueryParameter("page", page.toString())
        }.build(),
        apiHeaders,
    )

    private fun genreApiUrl(): HttpUrl.Builder =
        "$apiUrl/comic/filter/list".toHttpUrl().newBuilder()
            .addQueryParameter("size", DEFAULT_PAGE_SIZE.toString())

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // Search
    private fun searchApiUrl(): HttpUrl.Builder =
        "$apiUrl/search/index".toHttpUrl().newBuilder().addQueryParameter("source", "0")
            .addQueryParameter("size", DEFAULT_PAGE_SIZE.toString())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val ranking = filters.firstInstanceOrNull<RankingGroup>()
        val genres = filters.firstInstanceOrNull<GenreGroup>()
        val searchById = filters.firstInstanceOrNull<SearchByIdFilter>()?.state ?: false
        val url = when {
            query.isEmpty() && ranking != null && (ranking.state[0] as TimeFilter).state != 0 -> rankApiUrl().apply {
                ranking.state.filterIsInstance<QueryFilter>().forEach { it.addQuery(this) }
                addQueryParameter("page", page.toString())
            }.build()

            query.isEmpty() && genres != null -> genreApiUrl().apply {
                genres.state.filterIsInstance<QueryFilter>().forEach { it.addQuery(this) }
                addQueryParameter("page", page.toString())
            }.build()

            query.isNotBlank() && searchById && query.toIntOrNull()?.let { it > 0 } ?: false -> getMangaUrl(query)

            else -> searchApiUrl().apply {
                addQueryParameter("keyword", query)
                addQueryParameter("page", page.toString())
            }.build()
        }
        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url
        return if (url.toString().startsWith("$apiUrl/comic/rank/list")) {
            latestUpdatesParse(response)
        } else if (url.toString().startsWith("$apiUrl/comic/detail")) {
            MangasPage(listOf(mangaDetailsParse(response)), false)
        } else {
            // "$apiUrl/comic/filter/list" or "$apiUrl/search/index"
            response.parseAs<ResponseDto<PageDto>>().data.toMangasPage(url.queryParameter("page")!!.toInt())
        }
    }

    // Latest
    // "$apiUrl/comic/update/list/1/$page" is same content
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiUrl/comic/update/list/0/$page", apiHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<ResponseDto<List<PageItemDto>?>>().data
        if (mangas.isNullOrEmpty()) {
            throw Exception("没有更多结果了")
        }
        return MangasPage(mangas.map { it.toSManga() }, true)
    }

    override fun getFilterList() = FilterList(
        SearchByIdFilter(),
        RankingGroup(),
        Filter.Separator(),
        Filter.Header("分类(搜索/查看排行榜时无效)"),
        GenreGroup(),
    )

    private fun chapterCommentsUrl(comicId: String, chapterId: String) = "$apiUrl/viewpoint/list?comicId=$comicId&chapterId=$chapterId"

    companion object {
        val USE_CACHE = CacheControl.Builder().maxStale(170, TimeUnit.SECONDS).build()
        const val USERNAME_PREF = "USERNAME"
        const val PASSWORD_PREF = "PASSWORD"
        const val TOKEN_PREF = "TOKEN"
        const val COMMENTS_PREF = "COMMENTS"
        const val COMMENTS_FLAG = "COMMENTS"
        const val IMAGE_RETRY_FLAG = "IMAGE_RETRY"
        const val DEFAULT_PAGE_SIZE = 20
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            SwitchPreferenceCompat(screen.context).apply {
                key = COMMENTS_PREF
                title = "章末吐槽页"
                summary = "修改后，已加载的章节需要清除章节缓存才能生效。"
                setDefaultValue(false)
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = USERNAME_PREF
                title = "用户名"
                summary = "该配置被修改后，会清空令牌(Token)以便重新登录；如果登录失败，会清空该配置"
                setOnPreferenceChangeListener { _, _ ->
                    // clean token after username/password changed
                    preferences.edit().putString(TOKEN_PREF, "").apply()
                    apiHeaders = apiHeaders.newBuilder().setToken("").build()
                    true
                }
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = PASSWORD_PREF
                title = "密码"
                summary = "该配置被修改后，会清空令牌(Token)以便重新登录；如果登录失败，会清空该配置"
                setOnPreferenceChangeListener { _, _ ->
                    // clean token after username/password changed
                    preferences.edit().putString(TOKEN_PREF, "").apply()
                    apiHeaders = apiHeaders.newBuilder().setToken("").build()
                    true
                }
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = TOKEN_PREF
                title = "令牌(Token)"
                summary = "当前登录状态：${
                if (preferences.getString(TOKEN_PREF, "").isNullOrEmpty()) "未登录" else "已登录"
                }\n填写用户名和密码后，不会立刻尝试登录，会在下次请求时自动尝试"

                setEnabled(false)
            }.let(screen::addPreference)
        }
    }
}
