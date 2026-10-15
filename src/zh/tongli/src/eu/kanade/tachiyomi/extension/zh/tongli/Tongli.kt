package eu.kanade.tachiyomi.extension.zh.tongli

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.Response

@Source
abstract class Tongli :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl = "https://api.tongli.tw"

    private val preferences = getPreferences()

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$apiUrl/SellRanking/1")
        val mangas = response.parseAs<PopularResponseDto>().rankingSet[0].week.map {
            it.toSManga()
        }
        return MangasPage(mangas, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val responseDto = client.get(
            "$apiUrl/SellShelf/6e7e5b75-1acd-4b7c-0097-08d6179fc10a/$page?pageSize=20",
        ).parseAs<LatestResponseDto>()
        val mangas = responseDto.books.map {
            it.toSManga()
        }
        return MangasPage(mangas, responseDto.totalPage > responseDto.page)
    }

    // Search

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.queryParameter("id") ?: return null
        val isSerial = url.queryParameter("isSerials") ?: return null
        return getMangaDetails(id, isSerial)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("SearchStr", query)
            .build()

        val response = client.post("$apiUrl/Search", requestBody)
        val mangas = response.parseAs<List<MangaDto>>().map {
            it.toSManga()
        }
        return MangasPage(mangas, false)
    }

    // Related
    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga) = client.get(
        "$apiUrl/Book/MutualsLike/${manga.url.substringBefore(",")}",
    ).parseAs<List<MangaDto>>().map { it.toSManga() }

    // mangaUpdate

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = manga.url.substringBefore(",")
        val isSerial = manga.url.substringAfter(",")

        val updatedManga = async {
            if (fetchDetails) getMangaDetails(id, isSerial) else manga
        }
        val updatedChapters = async {
            if (fetchChapters) {
                client.get(
                    "$apiUrl/Book/BookVol/$id?bookID=null&isSerial=$isSerial",
                    headersBuilder().addToken(getToken()),
                ).parseAs<List<ChapterDto>>().mapNotNull {
                    it.toSChapter()
                }.reversed()
            } else {
                chapters
            }
        }
        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    private suspend fun getMangaDetails(id: String, isSerial: String) = client.get(
        "$apiUrl/Book?bookGroupID=$id&isSerial=$isSerial",
    ).parseAs<DetailsDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String {
        val bookGroupID = manga.url.substringBefore(",")
        val isSerial = manga.url.substringAfter(",")
        return "$baseUrl/book?id=$bookGroupID&isGroup=true&isSerials=$isSerial"
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter) = client.get(
        "$apiUrl/Comic/sas/${chapter.url}",
        headersBuilder().addToken(getToken()),
    ).parseAs<PageListResponseDto>().pages.mapIndexed { index, it ->
        Page(index, imageUrl = it.imageURL)
    }

    private suspend fun getToken(): String {
        val token = preferences.getString(PREF_TOKEN, "")!!
        val expires = preferences.getLong(PREF_EXPIRES, 0)
        val currentTimeMillis = System.currentTimeMillis()
        if (token.isEmpty()) {
            val email = preferences.getString(PREF_EMAIL, "")!!
            val password = preferences.getString(PREF_PASSWORD, "")!!
            if (email.isEmpty()) {
                return loginAnonymous()
            }
            return login(email, password)
        }
        if (expires < currentTimeMillis) {
            val refreshToken = preferences.getString(PREF_REFRESH_TOKEN, "")!!
            return refresh(refreshToken)
        }
        return token
    }

    private suspend fun login(email: String, password: String): String {
        val requestBody = buildJsonObject {
            put(PREF_EMAIL, email)
            put(PREF_PASSWORD, password)
            put("returnSecureToken", true)
        }.toJsonRequestBody()
        try {
            val response = client.post(VERIFY_PASSWORD_URL, requestBody)
            return saveToPreferences(response)
        } catch (e: SerializationException) {
            // Remove email/password after failed login
            preferences.edit()
                .putString(PREF_EMAIL, "")
                .putString(PREF_PASSWORD, "")
                .apply()
            throw Exception("登录失败")
        }
    }

    private suspend fun loginAnonymous(): String {
        val requestBody = buildJsonObject {
            put("returnSecureToken", true)
        }.toJsonRequestBody()
        val response = client.post(SIGNUP_URL, requestBody)
        return saveToPreferences(response)
    }

    private suspend fun refresh(refreshToken: String): String {
        val requestBody = buildJsonObject {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
        }.toJsonRequestBody()
        val response = client.post(REFRESH_TOKEN_URL, requestBody)
        return saveToPreferences(response)
    }

    private fun Headers.Builder.addToken(token: String) = add("Authorization: Bearer $token").build()

    private fun saveToPreferences(response: Response): String {
        val dto = response.parseAs<TokenResponseDto>()
        val currentTimeMillis = System.currentTimeMillis()
        preferences.edit()
            .putString(PREF_TOKEN, dto.idToken)
            .putString(PREF_REFRESH_TOKEN, dto.refreshToken)
            // Token expires after one hour
            .putLong(PREF_EXPIRES, currentTimeMillis + TOKEN_EXPIRES_MS)
            .apply()

        return dto.idToken
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            EditTextPreference(screen.context).apply {
                key = PREF_EMAIL
                title = "电子邮件"
                summary = "该配置被修改后，会清空令牌(Token)以便重新登录；如果登录失败，会清空该配置"
                setOnPreferenceChangeListener { _, _ ->
                    // clean token after email/password changed
                    preferences.edit().putString(PREF_TOKEN, "").apply()
                    true
                }
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = PREF_PASSWORD
                title = "密码"
                summary = "该配置被修改后，会清空令牌(Token)以便重新登录；如果登录失败，会清空该配置"
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                setOnPreferenceChangeListener { _, _ ->
                    // clean token after email/password changed
                    preferences.edit().putString(PREF_TOKEN, "").apply()
                    true
                }
            }.let(screen::addPreference)
        }
    }

    companion object {
        private const val PREF_TOKEN = "TOKEN"
        private const val PREF_REFRESH_TOKEN = "REFRESHTOKEN"
        private const val PREF_EXPIRES = "EXPIRES"
        private const val PREF_EMAIL = "EMAIL"
        private const val PREF_PASSWORD = "PASSWORD"

        private const val API_KEY = "AIzaSyAJbYmo7KyhM_7CDXjjFXnp8bdRTNgbUIE"

        private const val IDENTITY_TOOLKIT_URL =
            "https://www.googleapis.com/identitytoolkit/v3/relyingparty"
        private const val VERIFY_PASSWORD_URL =
            "$IDENTITY_TOOLKIT_URL/verifyPassword?key=$API_KEY"
        private const val SIGNUP_URL =
            "$IDENTITY_TOOLKIT_URL/signupNewUser?key=$API_KEY"
        private const val REFRESH_TOKEN_URL =
            "https://securetoken.googleapis.com/v1/token?key=$API_KEY"

        private const val TOKEN_EXPIRES_MS = 3_600_000L
    }
}
