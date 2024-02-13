package eu.kanade.tachiyomi.extension.en.hiperdex

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Hiperdex : Madara("Hiperdex", "https://hiperdex.com", "en") {
    override val useNewChapterEndpoint: Boolean = true

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = super.client.newBuilder()
        .addInterceptor(::domainChangeIntercept)
        .build()

    private var lastDomain = ""

    private fun domainChangeIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host !in listOf(preferences.baseUrlHost, lastDomain)) {
            return chain.proceed(request)
        }

        if (lastDomain.isNotEmpty()) {
            val newUrl = request.url.newBuilder()
                .host(preferences.baseUrlHost)
                .build()

            return chain.proceed(
                request.newBuilder()
                    .url(newUrl)
                    .build(),
            )
        }

        val response = chain.proceed(request)

        if (request.url.host == response.request.url.host) return response

        response.close()

        preferences.baseUrlHost = response.request.url.host

        lastDomain = request.url.host

        val newUrl = request.url.newBuilder()
            .host(response.request.url.host)
            .build()

        return chain.proceed(
            request.newBuilder()
                .url(newUrl)
                .build(),
        )
    }

    companion object {
        private const val defaultBaseUrlHost = "hiperdex.com"
        private const val RESTART_TACHIYOMI = "Restart Tachiyomi to apply new setting."
        private const val BASE_URL_PREF_TITLE = "Override BaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl_v2"
        private const val BASE_URL_PREF_SUMMARY = "Enter a complete url starting with http"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(defaultBaseUrlHost)
            dialogTitle = BASE_URL_PREF_TITLE

            setOnPreferenceChangeListener { _, newVal ->
                val url = newVal as String
                runCatching {
                    val host = url.toHttpUrl().host

                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    preferences.edit().putString(BASE_URL_PREF, host).commit()
                }
                false
            }
        }
        screen.addPreference(baseUrlPref)

        super.setupPreferenceScreen(screen)
    }

    private var SharedPreferences.baseUrlHost
        get() = getString(BASE_URL_PREF, defaultBaseUrlHost) ?: defaultBaseUrlHost
        set(newHost) {
            edit().putString(BASE_URL_PREF, newHost).commit()
        }

    private fun getPrefBaseUrl(): String = preferences.baseUrlHost.let { "https://$it" }
}
