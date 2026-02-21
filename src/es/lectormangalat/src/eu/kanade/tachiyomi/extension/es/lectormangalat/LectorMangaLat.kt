package eu.kanade.tachiyomi.extension.es.lectormangalat

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.lib.randomua.addRandomUAPreferenceToScreen
import keiyoushi.lib.randomua.getPrefCustomUA
import keiyoushi.lib.randomua.getPrefUAType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.getPreferences
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class LectorMangaLat :
    Madara(
        "LectorManga.lat",
        "https://lectormangaa.com",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ),
    ConfigurableSource {

    private val preferences = getPreferences()

    override val client: OkHttpClient = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val mangaSubString = "biblioteca"

    override val useNewChapterEndpoint = true

    override val pageListParseSelector = "div.reading-content div.page-break > img"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }
}
