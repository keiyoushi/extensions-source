package eu.kanade.tachiyomi.extension.ar.hentaislayer

import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.fuzzydoodle.FuzzyDoodle
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import okhttp3.OkHttpClient

@Source
abstract class HentaiSlayer :
    FuzzyDoodle(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val preferences by getPreferencesLazy()

    override fun latestPageUrl(page: Int) = "$baseUrl/latest-${getLatestTypes()}?page=$page"

    companion object {
        private const val LATEST_PREF = "LatestType"
        private val LATEST_PREF_ENTRIES get() = arrayOf(
            "مانجا",
            "مانهوا",
            "كوميكس",
        )
        private val LATEST_PREF_ENTRY_VALUES get() = arrayOf(
            "manga",
            "manhwa",
            "comics",
        )
        private val LATEST_PREF_DEFAULT = LATEST_PREF_ENTRY_VALUES[0]
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = LATEST_PREF
            title = "نوع القائمة الأحدث"
            summary = "حدد نوع الإدخالات التي سيتم الاستعلام عنها لأحدث قائمة. الأنواع الأخرى متوفرة في الشائع/التصفح أو البحث"
            entries = LATEST_PREF_ENTRIES
            entryValues = LATEST_PREF_ENTRY_VALUES
            setDefaultValue(LATEST_PREF_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(
                    screen.context,
                    ".لتطبيق الإعدادات الجديدة Tachiyomi أعد تشغيل",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.also(screen::addPreference)
    }

    private fun getLatestTypes(): String = preferences.getString(LATEST_PREF, LATEST_PREF_DEFAULT)!!
}
