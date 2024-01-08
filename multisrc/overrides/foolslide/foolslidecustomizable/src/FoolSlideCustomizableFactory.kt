package eu.kanade.tachiyomi.extension.all.foolslidecustomizable

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.source.SourceFactory

class FoolSlideCustomizableFactory : SourceFactory {
    override fun createSources() = listOf(FoolSlideCustomizable())
}

class FoolSlideCustomizable : FoolSlide("FoolSlide Customizable", "", "other") {
    override val baseUrl: String by lazy {
        preferences.getString(BASE_URL_PREF, DEFAULT_BASEURL)!!.substringBefore("/directory")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(DEFAULT_BASEURL)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $DEFAULT_BASEURL"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val DEFAULT_BASEURL = "https://127.0.0.1"
        private const val BASE_URL_PREF_TITLE = "Example URL: https://domain.com/path_to/directory/"
        private val BASE_URL_PREF = "overrideBaseUrl_v${AppInfo.getVersionName()}"
        private const val BASE_URL_PREF_SUMMARY = "Connect to a designated FoolSlide server"
        private const val RESTART_TACHIYOMI = "Restart Tachiyomi to apply new setting."
    }
}
