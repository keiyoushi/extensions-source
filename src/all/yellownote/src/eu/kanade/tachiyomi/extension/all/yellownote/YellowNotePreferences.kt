package eu.kanade.tachiyomi.extension.all.yellownote

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import keiyoushi.lib.i18n.Intl
import okhttp3.HttpUrl.Companion.toHttpUrl

object YellowNotePreferences {

    private const val PS_KEY_ROOT = "XChina"
    private const val PS_KEY_DOMAIN = "$PS_KEY_ROOT::DOMAIN"
    private const val PS_KEY_DOMAIN_DEFAULT = "$PS_KEY_DOMAIN::DEFAULT"
    private const val PS_KEY_DOMAIN_OVERRIDE = "$PS_KEY_DOMAIN::OVERRIDE"
    private const val PS_KEY_LANGUAGE = "$PS_KEY_ROOT::LANGUAGE"

    private const val DEFAULT_DOMAIN = "https://xchina.co"

    internal fun SharedPreferences.preferenceMigration() {
        // refresh when DEFAULT_DOMAIN update
        val defaultDomain = getString(PS_KEY_DOMAIN_DEFAULT, DEFAULT_DOMAIN)!!
        if (DEFAULT_DOMAIN != defaultDomain) {
            edit()
                .putString(PS_KEY_DOMAIN_DEFAULT, DEFAULT_DOMAIN)
                .putString(PS_KEY_DOMAIN_OVERRIDE, DEFAULT_DOMAIN)
                .apply()
        }
    }

    internal fun SharedPreferences.baseUrl(): String {
        val lang = language()
        val subdomain = LanguageUtils.getSubdomainByLanguage(lang)
        val httpUrl = getString(PS_KEY_DOMAIN_OVERRIDE, DEFAULT_DOMAIN)!!.toHttpUrl()
        val newHost = when {
            httpUrl.host.split('.').size > 2 || subdomain == null -> httpUrl.host
            else -> "$subdomain.${httpUrl.host}"
        }

        return httpUrl.newBuilder()
            .scheme("https")
            .host(newHost)
            .build()
            .toString()
            .removeSuffix("/")
    }

    internal fun SharedPreferences.language(): String = getString(PS_KEY_LANGUAGE, "")!!.ifBlank { LanguageUtils.getDefaultLanguage() }

    internal fun buildPreferences(context: Context, intl: Intl): List<Preference> = listOf(
        buildDomainPreference(context, intl),
        buildLanguagePreference(context, intl),
    )

    internal fun buildDomainPreference(context: Context, intl: Intl): Preference {
        return EditTextPreference(context).apply {
            key = PS_KEY_DOMAIN_OVERRIDE
            title = intl["config.domain.title"]
            summary = intl["config.domain.summary"]
            dialogTitle = intl["config.domain.dialog.title"]
            dialogMessage = "${intl["config.domain.dialog.message"]}$DEFAULT_DOMAIN"

            setDefaultValue(DEFAULT_DOMAIN)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    (newValue as String).toHttpUrl()
                } catch (_: IllegalArgumentException) {
                    Toast.makeText(
                        context,
                        intl["config.domain.toast.changed-failed"],
                        Toast.LENGTH_LONG,
                    ).show()
                    return@setOnPreferenceChangeListener false
                }

                Toast.makeText(
                    context,
                    intl["config.domain.toast.changed-success"],
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }
    }

    internal fun buildLanguagePreference(context: Context, intl: Intl): Preference = ListPreference(context).apply {
        key = PS_KEY_LANGUAGE
        title = intl["config.language.title"]
        summary = intl["config.language.summary"]
        entries = LanguageUtils.getSupportedLanguageDisplayNames()
        entryValues = LanguageUtils.getSupportedLanguageKeys()
        setDefaultValue("")
        setOnPreferenceChangeListener { _, newValue ->
            Toast.makeText(
                context,
                intl["config.language.changed-success"],
                Toast.LENGTH_LONG,
            ).show()
            true
        }
    }
}
