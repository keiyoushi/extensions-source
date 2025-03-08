package eu.kanade.tachiyomi.extension.ru.hentailib

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.libgroup.LibGroup
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.getPreferencesLazy

class HentaiLib : LibGroup("HentaiLib", "https://hentailib.me", "ru") {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private var domain: String = preferences.getString(DOMAIN_TITLE, DOMAIN_DEFAULT)!!
    override val baseUrl: String = domain

    override val siteId: Int = 4 // Important in api calls

    // Filters
    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().toMutableList()
        if (filters.size > 7) {
            filters.removeAt(7) // AgeList
        }
        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        EditTextPreference(screen.context).apply {
            key = DOMAIN_TITLE
            this.title = DOMAIN_TITLE
            summary = domain
            this.setDefaultValue(DOMAIN_DEFAULT)
            dialogTitle = DOMAIN_TITLE
            setOnPreferenceChangeListener { _, _ ->
                val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val DOMAIN_TITLE = "Домен"
        private const val DOMAIN_DEFAULT = "https://hentailib.me"
    }
}
