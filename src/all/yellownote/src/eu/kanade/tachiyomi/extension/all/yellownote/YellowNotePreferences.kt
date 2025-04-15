package eu.kanade.tachiyomi.extension.all.yellownote

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.lib.i18n.Intl
import okhttp3.HttpUrl.Companion.toHttpUrl

object YellowNotePreferences {

    private const val PS_KEY_ROOT = "XChina"
    private const val PS_KEY_DOMAIN = "$PS_KEY_ROOT::DOMAIN"

    private const val DEFAULT_BASE_URL = "https://xchina.co"

    internal fun SharedPreferences.baseUrl(subdomain: String?): String {
        val rawDomain = getString(PS_KEY_DOMAIN, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        val httpUrl = rawDomain.toHttpUrl()

        val newHost = when {
            httpUrl.host.split('.').size > 2 || subdomain == null -> httpUrl.host
            else -> "$subdomain.${httpUrl.host}"
        }

        return httpUrl.newBuilder()
            .host(newHost)
            .scheme("https")
            .build()
            .toString()
            .removeSuffix("/")
    }

    internal fun buildPreferences(context: Context, intl: Intl): List<EditTextPreference> {
        return listOf(
            EditTextPreference(context).apply {
                key = PS_KEY_DOMAIN
                title = intl["config.domain.title"]
                summary = intl["config.domain.summary"]
            },
        )
    }
}
