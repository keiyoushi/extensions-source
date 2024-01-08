package eu.kanade.tachiyomi.extension.ru.senkognito

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import eu.kanade.tachiyomi.multisrc.senkuro.Senkuro
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Senkognito : Senkuro("Senkognito", "https://senkognito.com", "ru") {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var domain: String? = if (preferences.getBoolean(redirect_PREF, true)) "https://senkognito.com" else "https://senkuro.com"
    override val baseUrl: String = domain.toString()
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val domainRedirect = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = redirect_PREF
            title = "Домен Senkognito"
            summary = "Отключите если домен Senkognito недоступен в браузере/WebView."
            setDefaultValue(true)
            setOnPreferenceChangeListener { _, newValue ->
                val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(domainRedirect)
    }

    companion object {
        private const val redirect_PREF = "domainRedirect"
    }
}
