package eu.kanade.tachiyomi.extension.ru.mangalib

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.libgroup.LibGroup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaLib : LibGroup("MangaLib", "https://mangalib.me", "ru") {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var domain: String = preferences.getString(DOMAIN_PREF, DOMAIN_DEFAULT)!!
    override val baseUrl: String = domain

    override val siteId: Int = 1 // Important in api calls

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        EditTextPreference(screen.context).apply {
            key = DOMAIN_PREF
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
        private const val DOMAIN_PREF = "MangaLibDomain"
        private const val DOMAIN_TITLE = "Домен"
        private const val DOMAIN_DEFAULT = "https://mangalib.me"
    }
}
