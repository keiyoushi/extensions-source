package eu.kanade.tachiyomi.extension.pt.flowermanga

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class FlowerManga :
    Madara(
        name = "Flower Manga",
        lang = "pt-BR",
        baseUrl = MIRRORS[DEFAULT_MIRROR],
        dateFormat = SimpleDateFormat("d 'de' MMMMM 'de' yyyy", Locale("pt", "BR")),
    ),
    ConfigurableSource {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = false

    override val baseUrl: String by lazy {
        when {
            System.getenv("CI") == "true" -> MIRRORS.joinToString("#, ")
            else -> {
                val index = preference.getString(MIRROR_KEY, "$DEFAULT_MIRROR")!!.toInt().coerceAtMost(MIRRORS.size - 1)
                MIRRORS[index]
            }
        }
    }

    private val preference: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_KEY
            title = "Domínios"
            summary = "%s\n Selecione um domínio - Necessário reiniciar o aplicativo"
            entries = MIRRORS
            entryValues = Array(MIRRORS.size) { it.toString() }
            setDefaultValue(DEFAULT_MIRROR)
        }.let(screen::addPreference)
    }

    companion object {
        private const val MIRROR_KEY = "MIRROR"
        private const val DEFAULT_MIRROR = 0
        private val MIRRORS = arrayOf("https://flowermanga.net", "https://flowermanga.com")
    }
}
