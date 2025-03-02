package eu.kanade.tachiyomi.extension.en.theblank

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.utils.getPreferences
import java.text.SimpleDateFormat
import java.util.Locale

class TheBlank :
    Madara(
        "The Blank Scanlation",
        "https://theblank.net",
        "en",
        dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US),
    ),
    ConfigurableSource {

    private val preferences = getPreferences()

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.vip-permission)"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }
}
