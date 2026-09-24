package eu.kanade.tachiyomi.extension.es.taurusfansub

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TaurusFansub :
    Madara(),
    ConfigurableSource {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds)

    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorStatus = "div.manga-status span:last-child"
    override val mangaDetailsSelectorDescription = "div.summary__content p"

    private val preferences: SharedPreferences = getPreferences()

    override fun chapterListSelector(): String {
        val baseSelector = super.chapterListSelector()
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

        if (!removePremium) {
            return baseSelector
        }

        return "$baseSelector:not(.scheduled)"
    }
    override val filterGenresSelector = ".manga-genres"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos de pago"
            summary = "Oculta automáticamente los capítulos que requieren pago."
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
    }
}
