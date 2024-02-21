package eu.kanade.tachiyomi.lib.configurablebaseurl

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 *  Helper Class to add configurable baseUrl to sources
 *
 *  @param defaultBaseUrl the default baseUrl to set, changing it will remove the preference value from users
 *  @param preferences SharedPreference object for the source
 *  @param lang source language
 */
class ConfigurableBaseUrl(
    private val defaultBaseUrl: String,
    private val preferences: SharedPreferences,
    lang: String,
) {
    init {
        preferences.clearOldBaseUrl()
    }

    /**
     *  Function to get the current baseUrl
     *
     *  usage:
     *  override val baseUrl get() = configurableBaseUrl.getBaseUrl()
     */
    fun getBaseUrl() = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    /**
     *  Function to add BaseUrl preference to the source
     */
    fun addBaseUrlPreference(screen: PreferenceScreen) = EditTextPreference(screen.context).apply {
        key = BASE_URL_PREF
        title = prefTitle
        setDefaultValue(defaultBaseUrl)

        setOnPreferenceChangeListener { _, newValue ->
            try {
                checkBaseUrl(newValue as String)
                true
            } catch (_: Throwable) {
                Toast.makeText(screen.context, invalidUrlMessage, Toast.LENGTH_LONG).show()
                false
            }
        }
    }.let(screen::addPreference)

    private val prefTitle = when (lang) {
        "ar" -> "تعديل الرابط"
        else -> "Configurable BaseUrl"
    }

    private val invalidUrlMessage = when (lang) {
        "ar" -> "رابط غير صالح"
        else -> "Invalid Url"
    }

    private fun SharedPreferences.clearOldBaseUrl() {
        if (getString(DEFAULT_BASE_URL_PREF, "")!! == defaultBaseUrl) return
        edit()
            .remove(BASE_URL_PREF)
            .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
            .apply()
    }

    private fun checkBaseUrl(url: String) {
        require(url == url.trim() && !url.endsWith('/'))
        val pathSegments = url.toHttpUrl().pathSegments
        require(pathSegments.size == 1 && pathSegments[0].isEmpty())
    }
}

private const val BASE_URL_PREF = "ConfigurableBaseUrl_pref"
private const val DEFAULT_BASE_URL_PREF = "ConfigurableBaseUrl_defaultBaseUrl"
