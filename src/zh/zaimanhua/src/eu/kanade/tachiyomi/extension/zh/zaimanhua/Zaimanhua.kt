package eu.kanade.tachiyomi.extension.zh.zaimanhua

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class Zaimanhua : HttpSource(), ConfigurableSource {
    override val lang = "zh"
    override val supportsLatest = true

    override val name = "再漫画"
    override val baseUrl = "https://manhua.zaimanhua.com"
    private val apiUrl = "https://v4api.zaimanhua.com/app/v1"
    private val accountApiUrl = "https://account-api.zaimanhua.com/v1"

    private val json by injectLazy<Json>()

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(5)
        .addInterceptor(::authIntercept)
        .addInterceptor(::imageRetryInterceptor)
        .build()

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "v4api.zaimanhua.com" || !request.headers["authorization"].isNullOrBlank()) {
            return chain.proceed(request)
        }

        var token: String = preferences.getString("TOKEN", "")!!
        if (token.isBlank() || !isValid(token)) {
            val username = preferences.getString("USERNAME", "")!!
            val password = preferences.getString("PASSWORD", "")!!
            token = getToken(username, password)
            if (token.isBlank()) {
                preferences.edit().putString("TOKEN", "").apply()
                preferences.edit().putString("USERNAME", "").apply()
                preferences.edit().putString("PASSWORD", "").apply()
                return chain.proceed(request)
            } else {
                preferences.edit().putString("TOKEN", token).apply()
                apiHeaders = apiHeaders.newBuilder().setToken(token).build()
            }
        }
        val authRequest = request.newBuilder().apply {
            header("authorization", "Bearer $token")
        }.build()
        return chain.proceed(authRequest)
    }

    private fun Headers.Builder.setToken(token: String): Headers.Builder = apply {
        if (token.isNotBlank()) set("authorization", "Bearer $token")
    }

    private var apiHeaders = headersBuilder().setToken(preferences.getString("TOKEN", "")!!).build()

    private fun isValid(token: String): Boolean {
        val response = client.newCall(
            GET(
                "$accountApiUrl/userInfo/get",
                headersBuilder().setToken(token).build(),
            ),
        ).execute().parseAs<ResponseDto<UserDto>>()
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
    // path: "/comic/detail/mangaId"
    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$apiUrl/comic/detail/${manga.url}", apiHeaders)

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
    // path: "/comic/chapter/mangaId/chapterId"
    private fun pageListApiRequest(path: String): Request =
        GET("$apiUrl/comic/chapter/$path", apiHeaders, USE_CACHE)

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val response = client.newCall(pageListApiRequest(chapter.url)).execute()
        val result = response.parseAs<ResponseDto<DataWrapperDto<ChapterImagesDto>>>()
        if (result.errmsg.isNotBlank()) {
            throw Exception(result.errmsg)
        } else {
            return Observable.just(
                result.data.data!!.images.mapIndexed { index, it ->
                    val fragment = json.encodeToString(ImageRetryParamsDto(chapter.url, index))
                    Page(index, imageUrl = "$it#$fragment")
                },
            )
        }
    }

    private fun imageRetryInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment
        if (response.isSuccessful || request.url.host != "images.zaimanhua.com" || fragment == null) return response
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

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Popular
    private fun rankApiUrl(): HttpUrl.Builder =
        "$apiUrl/comic/rank/list".toHttpUrl().newBuilder().addQueryParameter("by_time", "3")
            .addQueryParameter("tag_id", "0").addQueryParameter("rank_type", "0")

    override fun popularMangaRequest(page: Int): Request = GET(
        rankApiUrl().apply {
            addQueryParameter("page", page.toString())
        }.build(),
        apiHeaders,
    )

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // Search
    private fun searchApiUrl(): HttpUrl.Builder =
        "$apiUrl/search/index".toHttpUrl().newBuilder().addQueryParameter("source", "0")
            .addQueryParameter("size", "20")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
        searchApiUrl().apply {
            addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())
        }.build(),
        apiHeaders,
    )

    override fun searchMangaParse(response: Response): MangasPage =
        response.parseAs<ResponseDto<PageDto>>().data.toMangasPage()

    // Latest
    // "$apiUrl/comic/update/list/1/$page" is same content
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiUrl/comic/update/list/0/$page", apiHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<ResponseDto<List<PageItemDto>>>().data
        return MangasPage(mangas.map { it.toSManga() }, true)
    }

    companion object {
        val USE_CACHE = CacheControl.Builder().maxStale(170, TimeUnit.SECONDS).build()
    }
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            EditTextPreference(screen.context).apply {
                key = "USERNAME"
                title = "用户名"
                summary = "该配置被修改后，会清空令牌(Token)以便重新登录；如果登录失败，会清空该配置"
                setOnPreferenceChangeListener { _, _ ->
                    // clean token after username/password changed
                    preferences.edit().putString("TOKEN", "").apply()
                    true
                }
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = "PASSWORD"
                title = "密码"
                summary = "该配置被修改后，会清空令牌(Token)以便重新登录；如果登录失败，会清空该配置"
                setOnPreferenceChangeListener { _, _ ->
                    // clean token after username/password changed
                    preferences.edit().putString("TOKEN", "").apply()
                    true
                }
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = "TOKEN"
                title = "令牌(Token)"
                summary = "当前登录状态：${
                if (preferences.getString("TOKEN", "").isNullOrEmpty()) "未登录" else "已登录"
                }\n填写用户名和密码后，不会立刻尝试登录，会在下次请求时自动尝试"

                setEnabled(false)
            }.let(screen::addPreference)
        }
    }
}
