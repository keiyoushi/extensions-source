package eu.kanade.tachiyomi.extension.pt.hentaiteca

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class HentaiTeca :
    Madara(
        "Hentai Teca",
        "https://hentaiteca.net",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
    ),
    ConfigurableSource {

    private val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client: OkHttpClient = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .addCustomUA()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    /*
     * Using Custom UA also in WebView
     * */
    private fun Headers.Builder.addCustomUA(): Headers.Builder {
        preferences.getPrefCustomUA()
            .takeIf { !it.isNullOrBlank() }
            ?.let { set(UA_KEY, it) }
        return this
    }

    companion object {
        const val UA_KEY = "User-Agent"
    }
}
