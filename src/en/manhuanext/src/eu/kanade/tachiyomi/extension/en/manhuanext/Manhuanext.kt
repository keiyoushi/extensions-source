package eu.kanade.tachiyomi.extension.en.manhuanext

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.utils.getPreferencesLazy

class Manhuanext :
    Madara(
        "Manhuanext",
        "https://manhuanext.com",
        "en",
    ),
    ConfigurableSource {
    override val useNewChapterEndpoint = true
    private val preferences by getPreferencesLazy()
    private val hideChapters = if (preferences.getBoolean(HIDE_PREMIUM, true)) {
        "li.wp-manga-chapter:not(.premium-block)"
    } else {
        "li.wp-manga-chapter"
    }
    override fun chapterListSelector() = hideChapters

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PREMIUM
            title = HIDE_PREMIUM_TITLE
            summary = HIDE_PREMIUM_SUM
            setDefaultValue(true)
        }.let(screen::addPreference)
    }
    companion object {
        private const val HIDE_PREMIUM = "hide_premium_chapters"
        private const val HIDE_PREMIUM_TITLE = "Hide premium chapters"
        private const val HIDE_PREMIUM_SUM = "Restart the app to apply changes"
    }
}
