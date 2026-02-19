package eu.kanade.tachiyomi.extension.zh.tongli

import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class Tongli :
    HttpSource(),
    ConfigurableSource {
    override val name: String = "東立"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://ebook.tongli.com.tw"
    private val apiUrl = "https://api.tongli.tw"

    private val preferences: SharedPreferences = getPreferences()
    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/SellRanking/1", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<PopularResponseDto>().rankingSet[0].week.map {
            it.toSManga()
        }
        return MangasPage(mangas, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/SellShelf/6e7e5b75-1acd-4b7c-0097-08d6179fc10a/$page?pageSize=20", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseDto = response.parseAs<LatestResponseDto>()
        val mangas = responseDto.books.map {
            it.toSManga()
        }
        return MangasPage(mangas, responseDto.totalPage > responseDto.page)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("SearchStr", query)
            .build()
        return POST("$apiUrl/Search", headers, requestBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<MangaDto>>().map {
            it.toSManga()
        }
        return MangasPage(mangas, false)
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        val bookGroupID = manga.url.substringBefore(",")
        val isSerial = manga.url.substringAfter(",")
        return GET("$apiUrl/Book?bookGroupID=$bookGroupID&isSerial=$isSerial", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsDto>().toSManga()

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val newHeaders = headersBuilder().add("Authorization: Bearer ${getToken()}").build()
        val bookGroupID = manga.url.substringBefore(",")
        val isSerial = manga.url.substringAfter(",")
        return GET("$apiUrl/Book/BookVol/$bookGroupID?bookID=null&isSerial=$isSerial", newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<List<ChapterDto>>().mapNotNull {
        it.toSChapter()
    }.reversed()

    override fun getMangaUrl(manga: SManga): String {
        val bookGroupID = manga.url.substringBefore(",")
        val isSerial = manga.url.substringAfter(",")
        return "$baseUrl/book?id=$bookGroupID&isGroup=true&isSerials=$isSerial"
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder().add("Authorization: Bearer ${getToken()}").build()
        return GET("$apiUrl/Comic/sas/${chapter.url}", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PageListResponseDto>().pages.mapIndexed { index, it ->
        Page(index, imageUrl = it.imageURL)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun getToken(): String {
        val token = preferences.getString("TOKEN", "")!!
        val expires = preferences.getLong("EXPIRES", 0)
        val currentTimeMillis = System.currentTimeMillis()
        if (token.isEmpty()) {
            val email = preferences.getString("EMAIL", "")!!
            val password = preferences.getString("PASSWORD", "")!!
            if (email.isEmpty()) {
                return loginAnonymous()
            }
            return login(email, password)
        }
        if (expires < currentTimeMillis) {
            val refreshToken = preferences.getString("REFRESHTOKEN", "")!!
            return refresh(refreshToken)
        }
        return token
    }

    private fun login(email: String, password: String): String {
        val requestBody = buildJsonObject {
            put("email", email)
            put("password", password)
            put("returnSecureToken", true)
        }.toString().toRequestBody(jsonMediaType)
        val response: TokenResponseDto
        try {
            response = client.newCall(
                POST(
                    "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword?key=AIzaSyAJbYmo7KyhM_7CDXjjFXnp8bdRTNgbUIE",
                    headers,
                    requestBody,
                ),
            ).execute().parseAs<TokenResponseDto>()
        } catch (e: SerializationException) {
            // Remove email/password after failed login
            preferences.edit()
                .putString("EMAIL", "")
                .putString("PASSWORD", "")
                .apply()
            throw Exception("登录失败")
        }
        val currentTimeMillis = System.currentTimeMillis()
        preferences.edit()
            .putString("TOKEN", response.idToken)
            .putString("REFRESHTOKEN", response.refreshToken)
            // Token expires after one hour
            .putLong("EXPIRES", currentTimeMillis + 3600000)
            .apply()
        return response.idToken
    }

    private fun loginAnonymous(): String {
        val requestBody = buildJsonObject {
            put("returnSecureToken", true)
        }.toString().toRequestBody(jsonMediaType)
        val response = client.newCall(
            POST(
                "https://www.googleapis.com/identitytoolkit/v3/relyingparty/signupNewUser?key=AIzaSyAJbYmo7KyhM_7CDXjjFXnp8bdRTNgbUIE",
                headers,
                requestBody,
            ),
        ).execute().parseAs<TokenResponseDto>()
        val currentTimeMillis = System.currentTimeMillis()
        preferences.edit()
            .putString("TOKEN", response.idToken)
            .putString("REFRESHTOKEN", response.refreshToken)
            .putLong("EXPIRES", currentTimeMillis + 3600000)
            .apply()
        return response.idToken
    }

    private fun refresh(refreshToken: String): String {
        val requestBody = buildJsonObject {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
        }.toString().toRequestBody(jsonMediaType)
        val response = client.newCall(
            POST(
                "https://securetoken.googleapis.com/v1/token?key=AIzaSyAJbYmo7KyhM_7CDXjjFXnp8bdRTNgbUIE",
                headers,
                requestBody,
            ),
        ).execute().parseAs<TokenResponseDto>()
        val currentTimeMillis = System.currentTimeMillis()
        preferences.edit()
            .putString("TOKEN", response.idToken)
            .putString("REFRESHTOKEN", response.refreshToken)
            .putLong("EXPIRES", currentTimeMillis + 3600000)
            .apply()
        return response.idToken
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            EditTextPreference(screen.context).apply {
                key = "EMAIL"
                title = "电子邮件"
                summary = "该配置被修改后，会清空令牌(Token)以便重新登录；如果登录失败，会清空该配置"
                setOnPreferenceChangeListener { _, _ ->
                    // clean token after email/password changed
                    preferences.edit().putString("TOKEN", "").apply()
                    true
                }
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = "PASSWORD"
                title = "密码"
                summary = "该配置被修改后，会清空令牌(Token)以便重新登录；如果登录失败，会清空该配置"
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                setOnPreferenceChangeListener { _, _ ->
                    // clean token after email/password changed
                    preferences.edit().putString("TOKEN", "").apply()
                    true
                }
            }.let(screen::addPreference)
        }
    }
}
