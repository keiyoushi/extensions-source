package eu.kanade.tachiyomi.extension.id.mangakuri

import android.text.InputType
import android.util.Base64
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.loneseal.ChapterPagesResponseDto
import eu.kanade.tachiyomi.multisrc.loneseal.LoneSeal
import eu.kanade.tachiyomi.multisrc.loneseal.UrlLayout
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class Mangakuri :
    LoneSeal(),
    ConfigurableSource {
    override val urlLayout = UrlLayout.LEGACY_COMIC
    override val overloadedGenres = super.overloadedGenres + setOf("yaoi")
    override val includeOrientationFilter = true

    private val preferences by getPreferencesLazy()
    private val email get() = preferences.getString(EMAIL_PREF, "")!!
    private val password get() = preferences.getString(PASSWORD_PREF, "")!!
    private val tokenMutex = Mutex()
    private var loginFailed = false
    private var loginRetryAt = Instant.DISTANT_PAST

    // The API allows 60 chapter requests a minute
    override fun OkHttpClient.Builder.configureClient() = addUnscrambleInterceptor()
        .rateLimit(60, 1.minutes) { it.host == apiUrl.toHttpUrl().host && "chapter" in it.pathSegments }

    override suspend fun Headers.Builder.configureApiHeaders() = apply {
        token()?.let { set("Authorization", "Bearer $it") }
    }

    override fun onEmptyPages(dto: ChapterPagesResponseDto) {
        val chapter = dto.chapter
        when {
            chapter.rateLimited -> error("Terlalu banyak permintaan, coba lagi nanti")
            chapter.passwordRequired -> error("Bab ini memerlukan kata sandi")
            loginFailed -> error("Email atau kata sandi salah")
            chapter.loginRequired && preferences.contains(TOKEN_PREF) -> {
                preferences.edit().remove(TOKEN_PREF).apply()
                error("Sesi login berakhir, coba lagi")
            }
            Clock.System.now() < loginRetryAt -> error("Gagal masuk, coba lagi nanti")
            chapter.isLocked && hasCredentials() -> error("Bab berbayar yang belum dibeli dengan akun ini")
            chapter.isLocked -> error("Bab berbayar. Masuk di pengaturan sumber dengan akun yang sudah membelinya")
            chapter.loginRequired -> error("Masukkan email dan kata sandi di pengaturan sumber")
        }
    }

    private fun hasCredentials() = email.isNotEmpty() && password.isNotEmpty()

    private suspend fun token(): String? = tokenMutex.withLock {
        if (loginFailed || !hasCredentials() || Clock.System.now() < loginRetryAt) return null

        preferences.getString(TOKEN_PREF, null)
            ?.takeIf { Clock.System.now() < it.expiry() - TOKEN_EXPIRY_MARGIN }
            ?.let { return it }

        val token = try {
            client.post("$apiUrl/auth/login", Fetch.XHR, LoginRequestDto(email, password).toJsonRequestBody())
                .parseAs<LoginResponseDto>()
                .token
        } catch (e: HttpException) {
            if (e.code == 401) loginFailed = true else loginRetryAt = Clock.System.now() + LOGIN_RETRY_DELAY
            return null
        } catch (_: IOException) {
            loginRetryAt = Clock.System.now() + LOGIN_RETRY_DELAY
            return null
        }
        preferences.edit().putString(TOKEN_PREF, token).apply()
        token
    }

    private fun String.expiry(): Instant {
        val payload = Base64.decode(split('.')[1], Base64.URL_SAFE)
        return Instant.fromEpochSeconds(payload.decodeToString().parseAs<JwtPayloadDto>().exp)
    }

    private fun clearLogin() {
        loginFailed = false
        loginRetryAt = Instant.DISTANT_PAST
        preferences.edit().remove(TOKEN_PREF).apply()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = EMAIL_PREF
            title = "Email"
            summary = "Untuk bab yang memerlukan login atau sudah dibeli"
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
            setOnPreferenceChangeListener { _, _ ->
                clearLogin()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "Kata sandi"
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
            setOnPreferenceChangeListener { _, _ ->
                clearLogin()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val EMAIL_PREF = "email"
        private const val PASSWORD_PREF = "password"
        private const val TOKEN_PREF = "token"
        private val TOKEN_EXPIRY_MARGIN = 1.hours
        private val LOGIN_RETRY_DELAY = 5.minutes
    }
}
