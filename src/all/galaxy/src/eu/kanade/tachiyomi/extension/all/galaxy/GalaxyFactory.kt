package eu.kanade.tachiyomi.extension.all.galaxy

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.SourceFactory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GalaxyFactory : SourceFactory {

    class GalaxyWebtoon : Galaxy("Galaxy Webtoon", "https://galaxyaction.net", "en") {
        override val id = 2602904659965278831
    }

    class GalaxyManga :
        Galaxy("Galaxy Manga", "https://galaxymanga.net", "ar"),
        ConfigurableSource {
        override val id = 2729515745226258240

        override val baseUrl by lazy { getPrefBaseUrl() }

        private val preferences: SharedPreferences by lazy {
            Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
        }

        companion object {
            private const val RESTART_APP = ".لتطبيق الإعدادات الجديدة أعد تشغيل التطبيق"
            private const val BASE_URL_PREF_TITLE = "تعديل الرابط"
            private const val BASE_URL_PREF = "overrideBaseUrl"
            private const val BASE_URL_PREF_SUMMARY = ".للاستخدام المؤقت. تحديث التطبيق سيؤدي الى حذف الإعدادات"
            private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        }

        override fun setupPreferenceScreen(screen: PreferenceScreen) {
            val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
                key = BASE_URL_PREF
                title = BASE_URL_PREF_TITLE
                summary = BASE_URL_PREF_SUMMARY
                this.setDefaultValue(super.baseUrl)
                dialogTitle = BASE_URL_PREF_TITLE
                dialogMessage = "Default: ${super.baseUrl}"

                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                    true
                }
            }
            screen.addPreference(baseUrlPref)
        }
        private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

        init {
            preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
                if (prefDefaultBaseUrl != super.baseUrl) {
                    preferences.edit()
                        .putString(BASE_URL_PREF, super.baseUrl)
                        .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                        .apply()
                }
            }
        }
    }

    override fun createSources() = listOf(
        GalaxyWebtoon(),
        GalaxyManga(),
    )
}
