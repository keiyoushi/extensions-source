package eu.kanade.tachiyomi.extension.ja.ganma

import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class GanmaApp(private val metadata: Metadata) : Ganma() {

    override val client = network.client.newBuilder()
        .cookieJar(Cookies(metadata.baseUrl.toHttpUrl().host, metadata.cookieName))
        .build()

    private val appHeaders: Headers = Headers.Builder().apply {
        add("User-Agent", metadata.userAgent)
        add("X-From", metadata.baseUrl)
    }.build()

    override fun chapterListRequest(manga: SManga): Request {
        checkSession()
        return GET(metadata.baseUrl + String.format(metadata.magazineUrl, manga.url.mangaId()), appHeaders)
    }

    override fun List<SChapter>.sortedDescending() = this

    override fun pageListRequest(chapter: SChapter): Request {
        checkSession()
        val (mangaId, chapterId) = chapter.url.chapterDir()
        return GET(metadata.baseUrl + String.format(metadata.storyUrl, mangaId, chapterId), appHeaders)
    }

    override fun pageListParse(chapter: SChapter, response: Response): List<Page> =
        try {
            response.parseAs<AppStory>().toPageList()
        } catch (e: Exception) {
            throw Exception("Chapter not available!")
        }

    private fun checkSession() {
        val expiration = preferences.getLong(SESSION_EXPIRATION_PREF, 0)
        if (System.currentTimeMillis() + 60 * 1000 <= expiration) return // at least 1 minute
        var field1 = preferences.getString(TOKEN_FIELD1_PREF, "")!!
        var field2 = preferences.getString(TOKEN_FIELD2_PREF, "")!!
        if (field1.isEmpty() || field2.isEmpty()) {
            val response = client.newCall(POST(metadata.baseUrl + metadata.tokenUrl, appHeaders)).execute()
            val token: Map<String, String> = response.parseAs()
            field1 = token[metadata.tokenField1]!!
            field2 = token[metadata.tokenField2]!!
        }
        val requestBody = FormBody.Builder().apply {
            add(metadata.tokenField1, field1)
            add(metadata.tokenField2, field2)
        }.build()
        val response = client.newCall(POST(metadata.baseUrl + metadata.sessionUrl, appHeaders, requestBody)).execute()
        val session: Session = response.parseAs()
        preferences.edit().apply {
            putString(TOKEN_FIELD1_PREF, field1)
            putString(TOKEN_FIELD2_PREF, field2)
            putLong(SESSION_EXPIRATION_PREF, session.expire)
        }.apply()
    }

    private fun clearSession(clearToken: Boolean) {
        preferences.edit().apply {
            putString(SESSION_PREF, "")
            putLong(SESSION_EXPIRATION_PREF, 0)
            if (clearToken) {
                putString(TOKEN_FIELD1_PREF, "")
                putString(TOKEN_FIELD2_PREF, "")
            }
        }.apply()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        SwitchPreferenceCompat(screen.context).apply {
            title = "Clear session"
            setOnPreferenceChangeListener { _, _ ->
                clearSession(clearToken = false)
                Toast.makeText(screen.context, "Session cleared", Toast.LENGTH_SHORT).show()
                false
            }
        }.let { screen.addPreference(it) }
        SwitchPreferenceCompat(screen.context).apply {
            title = "Clear token"
            setOnPreferenceChangeListener { _, _ ->
                clearSession(clearToken = true)
                Toast.makeText(screen.context, "Token cleared", Toast.LENGTH_SHORT).show()
                false
            }
        }.let { screen.addPreference(it) }
    }

    class Cookies(private val host: String, private val name: String) : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (url.host != host) return emptyList()
            val cookie = Cookie.Builder().apply {
                name(name)
                value(preferences.getString(SESSION_PREF, "")!!)
                domain(host)
            }.build()
            return listOf(cookie)
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (url.host != host) return
            for (cookie in cookies) {
                if (cookie.name == name) {
                    preferences.edit().putString(SESSION_PREF, cookie.value).apply()
                }
            }
        }
    }

    companion object {
        private const val TOKEN_FIELD1_PREF = "TOKEN_FIELD1"
        private const val TOKEN_FIELD2_PREF = "TOKEN_FIELD2"
        private const val SESSION_PREF = "SESSION"
        private const val SESSION_EXPIRATION_PREF = "SESSION_EXPIRATION"
    }
}
