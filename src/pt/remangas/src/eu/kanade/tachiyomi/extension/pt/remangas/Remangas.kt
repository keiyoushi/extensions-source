package eu.kanade.tachiyomi.extension.pt.remangas

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.lib.randomua.addRandomUAPreferenceToScreen
import keiyoushi.lib.randomua.getPrefCustomUA
import keiyoushi.lib.randomua.getPrefUAType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.getPreferencesLazy
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Remangas :
    Madara(
        "Remangas",
        "https://remangas.net",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
    ),
    ConfigurableSource {

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client: OkHttpClient = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(2)
        .readTimeout(1, TimeUnit.MINUTES)
        .connectTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }
}
