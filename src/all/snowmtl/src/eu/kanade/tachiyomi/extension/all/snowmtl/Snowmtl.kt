package eu.kanade.tachiyomi.extension.all.snowmtl

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.snowmtl.interceptors.TranslationInterceptor
import eu.kanade.tachiyomi.extension.all.snowmtl.translator.TranslatorEngine
import eu.kanade.tachiyomi.extension.all.snowmtl.translator.bing.BingTranslator
import eu.kanade.tachiyomi.extension.all.snowmtl.translator.google.GoogleTranslator
import eu.kanade.tachiyomi.multisrc.machinetranslations.MachineTranslations
import eu.kanade.tachiyomi.multisrc.machinetranslations.interceptors.ComposedImageInterceptor
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.O)
class Snowmtl(
    private val language: LanguageSetting,
) : MachineTranslations(
    name = "Snow Machine Translations",
    baseUrl = "https://snowmtl.ru",
    language,
) {
    override val lang = language.lang

    private val translators = arrayOf(
        "Bing",
        "Google",
    )

    private val settings: LanguageSetting get() = language.copy(
        fontSize = this@Snowmtl.fontSize,
        disableSourceSettings = this@Snowmtl.disableSourceSettings,
    )

    private val provider: String get() =
        preferences.getString(TRANSLATOR_PROVIDER_PREF, translators.first())!!

    private val clientUtils = network.cloudflareClient.newBuilder()
        .rateLimit(3, 2, TimeUnit.SECONDS)
        .build()

    override val useDefaultComposedImageInterceptor = false

    override fun clientBuilder(): OkHttpClient.Builder {
        val translator: TranslatorEngine = when (provider) {
            "Google" -> GoogleTranslator(clientUtils, headers)
            else -> BingTranslator(clientUtils, headers)
        }

        return super.clientBuilder()
            .rateLimit(3)
            .addInterceptor(TranslationInterceptor(settings, translator))
            .addInterceptor(ComposedImageInterceptor(baseUrl, settings))
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)

        if (language.target == language.origin) {
            return
        }

        ListPreference(screen.context).apply {
            key = TRANSLATOR_PROVIDER_PREF
            title = "Translator"
            entries = translators
            entryValues = translators
            summary = buildString {
                appendLine("Engine used to translate dialog boxes")
                append("\t* %s")
            }

            setDefaultValue(translators.first())

            setOnPreferenceChange { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String

                Toast.makeText(
                    screen.context,
                    "The translator has been changed to '$entry'",
                    Toast.LENGTH_LONG,
                ).show()

                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val TRANSLATOR_PROVIDER_PREF = "translatorProviderPref"
    }
}
