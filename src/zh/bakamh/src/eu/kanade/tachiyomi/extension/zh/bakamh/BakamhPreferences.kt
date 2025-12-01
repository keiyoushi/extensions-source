package eu.kanade.tachiyomi.extension.zh.bakamh

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import okhttp3.HttpUrl.Companion.toHttpUrl

object BakamhPreferences {

    private const val PS_KEY_ROOT = "BAKAMH"
    private const val PS_KEY_DOMAIN = "$PS_KEY_ROOT::DOMAIN"
    private const val PS_KEY_DOMAIN_DEFAULT = "$PS_KEY_DOMAIN::DEFAULT"
    private const val PS_KEY_DOMAIN_OVERRIDE = "$PS_KEY_DOMAIN::OVERRIDE"

    const val DEFAULT_DOMAIN = "https://bakamh.com"

    internal fun SharedPreferences.preferenceMigration() {
        // refresh when DEFAULT_DOMAIN update
        refreshDefaultValue(PS_KEY_DOMAIN_DEFAULT, PS_KEY_DOMAIN_OVERRIDE, DEFAULT_DOMAIN)
    }

    private fun SharedPreferences.refreshDefaultValue(
        key: String,
        overrideKey: String,
        defaultValue: String,
    ) {
        val current = getString(key, defaultValue)!!
        if (current != defaultValue) {
            edit()
                .putString(key, defaultValue)
                .putString(overrideKey, defaultValue)
                .apply()
        }
    }

    internal fun SharedPreferences.baseUrl(): String {
        val httpUrl = getString(PS_KEY_DOMAIN_OVERRIDE, DEFAULT_DOMAIN)!!.toHttpUrl()

        return httpUrl.newBuilder()
            .scheme("https")
            .host(httpUrl.host)
            .build()
            .toString()
            .removeSuffix("/")
    }

    internal fun buildPreferences(context: Context): List<Preference> {
        return listOf(
            buildDomainPreference(context),
        )
    }

    internal fun buildDomainPreference(context: Context): Preference {
        return EditTextPreference(context).apply {
            key = PS_KEY_DOMAIN_OVERRIDE
            title = "域名设置"
            summary = "如果当前域名无法访问，可在此处设置，重启应用后生效。"
            dialogTitle = title
            dialogMessage = "域名可在发布页获取，默认域名为 $DEFAULT_DOMAIN"

            setDefaultValue(DEFAULT_DOMAIN)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    (newValue as String).toHttpUrl()
                } catch (_: IllegalArgumentException) {
                    Toast.makeText(
                        context,
                        "设置域名失败！",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@setOnPreferenceChangeListener false
                }

                Toast.makeText(
                    context,
                    "域名设置为 $newValue ！重启应用后生效。",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }
    }
}
