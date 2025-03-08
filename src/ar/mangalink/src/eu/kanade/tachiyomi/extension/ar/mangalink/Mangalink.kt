package eu.kanade.tachiyomi.extension.ar.mangalink

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.utils.getPreferencesLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Mangalink :
    Madara(
        "مانجا لينك",
        "https://link-manga.com",
        "ar",
        SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    ),
    ConfigurableSource {

    override val chapterUrlSuffix = ""
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences: SharedPreferences by getPreferencesLazy()

    companion object {
        private const val RESTART_TACHIYOMI = ".لتطبيق الإعدادات الجديدة Tachiyomi أعد تشغيل"
        private const val BASE_URL_PREF_TITLE = "تعديل الرابط"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF_SUMMARY = ".للاستخدام المؤقت. تحديث التطبيق سيؤدي الى حذف الإعدادات"
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
                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
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
