package eu.kanade.tachiyomi.extension.all.projectsuki

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

/**
 *  @see EXTENSION_INFO Found in ProjectSuki.kt
 */
@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

/**
 * @author Federico d'Alonzo &lt;me@npgx.dev&gt;
 */
class ProjectSukiPreferences(id: Long) {

    internal val shared by lazy { Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000) }

    abstract inner class PSPreference<Raw : Any, T : Any>(val preferenceIdentifier: String, val default: Raw) {

        abstract val rawGet: SharedPreferences.(identifier: String, default: Raw) -> Raw
        abstract fun Raw.transform(): T
        abstract fun PreferenceScreen.constructPreference(): Preference

        protected inline fun summary(block: () -> String): String = block().trimIndent()

        operator fun invoke(): T = shared.rawGet(preferenceIdentifier, default).transform()
    }

    val defaultSearchMode = object : PSPreference<String, ProjectSukiFilters.SearchMode>("$SHORT_FORM_ID-default-search-mode", ProjectSukiFilters.SearchMode.SMART.display) {
        override val rawGet: SharedPreferences.(identifier: String, default: String) -> String = { id, def -> getString(id, def)!! }
        override fun String.transform(): ProjectSukiFilters.SearchMode = ProjectSukiFilters.SearchMode.values()
            .firstOrNull { it.display == this } ?: ProjectSukiFilters.SearchMode.SMART

        override fun PreferenceScreen.constructPreference() = ListPreference(context).apply {
            key = preferenceIdentifier
            entries = ProjectSukiFilters.SearchMode.values().map { it.display }.toTypedArray()
            entryValues = ProjectSukiFilters.SearchMode.values().map { it.display }.toTypedArray()
            setDefaultValue(ProjectSukiFilters.SearchMode.SMART.display)
            title = "Default search mode"
            summary = summary {
                """
                Select which Search Mode to use by default. Can be useful for global searches. ${ProjectSukiFilters.SearchMode.SMART} is recommended.
                 - ${ProjectSukiFilters.SearchMode.SMART}: ${ProjectSukiFilters.SearchMode.SMART.run { description() }}
                 - ${ProjectSukiFilters.SearchMode.SIMPLE}: ${ProjectSukiFilters.SearchMode.SIMPLE.run { description() }}
                 - ${ProjectSukiFilters.SearchMode.FULL_SITE}: ${ProjectSukiFilters.SearchMode.FULL_SITE.run { description() }}
                """.trimIndent()
            }
        }
    }

    val whitelistedLanguages = object : PSPreference<String, Set<String>>("$SHORT_FORM_ID-languages-whitelist", "") {
        override val rawGet: SharedPreferences.(identifier: String, default: String) -> String = { id, def -> getString(id, def)!! }
        override fun String.transform(): Set<String> {
            return split(',')
                .filter { it.isNotBlank() }
                .mapTo(HashSet()) { it.trim().lowercase(Locale.US) }
        }

        override fun PreferenceScreen.constructPreference() = EditTextPreference(context).apply {
            key = preferenceIdentifier
            title = "Whitelisted languages"
            dialogTitle = "Include chapters in the following languages:"
            dialogMessage = "Enter the languages you want to include by separating them with a comma ',' (e.g. \"English, SPANISH, gReEk\", without quotes (\"))."
            summary = summary {
                """
                NOTE: You will need to refresh comics that have already been fetched!! (drag down in the comic page in tachiyomi)

                When empty will allow all languages (see blacklisting).
                It will match the string present in the "Language" column of the chapter (NOT case sensitive).
                Chapters that do not have a "Language" column, will be listed as "$UNKNOWN_LANGUAGE", which is always whitelisted (see blacklisting).
                """
            }
        }
    }

    val blacklistedLanguages = object : PSPreference<String, Set<String>>("$SHORT_FORM_ID-languages-blacklist", "") {
        override val rawGet: SharedPreferences.(identifier: String, default: String) -> String = { id, def -> getString(id, def)!! }
        override fun String.transform(): Set<String> {
            return split(",")
                .filter { it.isNotBlank() }
                .mapTo(HashSet()) { it.trim().lowercase(Locale.US) }
        }

        override fun PreferenceScreen.constructPreference() = EditTextPreference(context).apply {
            key = preferenceIdentifier
            title = "Blacklisted languages"
            dialogTitle = "Exclude chapters in the following languages:"
            dialogMessage = "Enter the languages you want to exclude by separating them with a comma ',' (e.g. \"English, SPANISH, gReEk\", without quotes (\"))."
            summary = summary {
                """
                NOTE: You will need to refresh comics that have already been fetched!! (drag down in the comic page in tachiyomi)

                When a language is in BOTH whitelist and blacklist, it will be EXCLUDED.
                It will match the string present in the "Language" column of the chapter (NOT case sensitive).
                Chapters that do not have a "Language" column, will be listed as "$UNKNOWN_LANGUAGE", you can exclude them by adding "unknown" to the list (e.g. "Chinese, unknown, Alienese").
                """.trimIndent()
            }
        }
    }

    fun PreferenceScreen.configure() {
        addRandomUAPreferenceToScreen(this)

        addPreference(defaultSearchMode.run { constructPreference() })
        addPreference(whitelistedLanguages.run { constructPreference() })
        addPreference(blacklistedLanguages.run { constructPreference() })
    }
}
