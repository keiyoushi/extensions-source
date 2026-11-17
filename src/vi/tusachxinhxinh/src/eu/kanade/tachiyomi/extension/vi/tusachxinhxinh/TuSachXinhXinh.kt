package eu.kanade.tachiyomi.extension.vi.tusachxinhxinh

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.webkit.CookieManager
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.koi.Koi
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class TuSachXinhXinh :
    Koi(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override suspend fun getPageList(chapter: SChapter): List<Page> = try {
        super.getPageList(chapter)
    } catch (e: Exception) {
        val hasCredentials = preferences.getString(PREF_USERNAME, "")?.isNotBlank() == true &&
            preferences.getString(PREF_PASSWORD, "")?.isNotBlank() == true
        if (e.message?.contains("cần đăng nhập") == true && hasCredentials) {
            login()
            super.getPageList(chapter)
        } else {
            throw e
        }
    }

    private suspend fun login(
        user: String = preferences.getString(PREF_USERNAME, "").orEmpty(),
        pass: String = preferences.getString(PREF_PASSWORD, "").orEmpty(),
    ): Boolean {
        if (isLoggedIn()) {
            throw Exception("Bạn đã đăng nhập trước đó")
        }

        if (user.isBlank() || pass.isBlank()) {
            throw Exception("Vui lòng nhập tên đăng nhập và mật khẩu")
        }

        val loginUrl = "$baseUrl/tai-khoan/"
        val doc = client.get(loginUrl, Fetch.DOCUMENT).asJsoup()
        val nonce = doc.selectFirst("input[name=user-registration-login-nonce]")?.attr("value")
            ?: throw Exception("Bạn đã đăng nhập trước đó")

        val formBody = FormBody.Builder()
            .add("username", user)
            .add("password", pass)
            .add("user-registration-login-nonce", nonce)
            .add("_wp_http_referer", "/tai-khoan/")
            .add("login", "Login")
            .add("rememberme", "forever")
            .add("redirect", "")
            .build()

        val response = client.post(loginUrl, Fetch.DOCUMENT, formBody, ensureSuccess = false)
        val resDoc = response.asJsoup()
        val errorMsg = resDoc.selectFirst("ul.user-registration-error li")?.text()
        if (errorMsg != null) {
            throw Exception(errorMsg)
        }

        CookieManager.getInstance().flush()
        return isLoggedIn()
    }

    private fun isLoggedIn(): Boolean = client.cookieJar
        .loadForRequest(baseUrl.toHttpUrl())
        .any { it.name.startsWith("wordpress_logged_in_") }

    private fun logout() {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(baseUrl)?.split(";") ?: emptyList()
        for (cookie in cookies) {
            val name = cookie.substringBefore("=").trim()
            if (name.startsWith("wordpress_logged_in_") || name.startsWith("wordpress_sec_")) {
                cookieManager.setCookie(baseUrl, "$name=; Max-Age=0; Path=/")
            }
        }
        cookieManager.flush()
    }

    private fun createPreference(context: Context): Preference = Preference::class.java.getConstructor(Context::class.java).newInstance(context)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        EditTextPreference(context).apply {
            key = PREF_USERNAME
            title = "Tên đăng nhập / Email"
            val current = preferences.getString(key, "").orEmpty()
            summary = if (current.isEmpty()) "Chưa thiết lập" else current
            dialogTitle = title
            setOnPreferenceChangeListener { pref, newValue ->
                val newStr = (newValue as String).trim()
                (pref as EditTextPreference).summary = if (newStr.isEmpty()) "Chưa thiết lập" else newStr
                true
            }
        }.let(screen::addPreference)

        EditTextPreference(context).apply {
            key = PREF_PASSWORD
            title = "Mật khẩu"
            summary = if (preferences.getString(key, "").isNullOrEmpty()) "Chưa thiết lập" else "••••••••"
            dialogTitle = title
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { pref, newValue ->
                (pref as EditTextPreference).summary = if ((newValue as String).isEmpty()) "Chưa thiết lập" else "••••••••"
                true
            }
        }.let(screen::addPreference)

        val loginPref = createPreference(context).apply {
            title = "Đăng nhập ngay"
            summary = if (isLoggedIn()) "Trạng thái: Đã đăng nhập" else "Nhấn để thực hiện đăng nhập"
            setOnPreferenceClickListener { pref ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        login()
                        withContext(Dispatchers.Main) {
                            pref.summary = "Trạng thái: Đã đăng nhập"
                            Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Đăng nhập thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                true
            }
        }.also(screen::addPreference)

        createPreference(context).apply {
            title = "Đăng xuất"
            summary = "Xóa phiên đăng nhập hiện tại"
            setOnPreferenceClickListener {
                logout()
                loginPref.summary = "Nhấn để thực hiện đăng nhập"
                Toast.makeText(context, "Đã đăng xuất!", Toast.LENGTH_SHORT).show()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_USERNAME = "pref_username"
        private const val PREF_PASSWORD = "pref_password"
    }
}
