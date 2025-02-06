package eu.kanade.tachiyomi.extension.all.snowmtl

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.snowmtl.interceptors.TranslationInterceptor
import eu.kanade.tachiyomi.extension.all.snowmtl.translator.BingTranslator
import eu.kanade.tachiyomi.extension.all.snowmtl.translator.TranslatorEngine
import eu.kanade.tachiyomi.multisrc.machinetranslations.MachineTranslations
import eu.kanade.tachiyomi.multisrc.machinetranslations.interceptors.ComposedImageInterceptor
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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

    private var disableTranslationOptimization: Boolean
        get() = preferences.getBoolean(DISABLE_TRANSLATION_OPTIM_PREF, language.disableTranslationOptimization)
        set(value) = preferences.edit().putBoolean(DISABLE_TRANSLATION_OPTIM_PREF, value).apply()

    private val settings: LanguageSetting get() = language.copy(
        fontSize = this@Snowmtl.fontSize,
        disableTranslationOptimization = this@Snowmtl.disableTranslationOptimization,
        disableSourceSettings = this@Snowmtl.disableSourceSettings,
    )

    private val clientUtils = network.cloudflareClient.newBuilder()
        .rateLimit(3, 2, TimeUnit.SECONDS)
        .build()

    private val translator: TranslatorEngine = BingTranslator(clientUtils, headers)

    // Keeps object state
    private val composeInterceptor = ComposedImageInterceptor(baseUrl, settings)
    private val translatorInterceptor = TranslationInterceptor(settings, translator)

    override val useDefaultComposedImageInterceptor = false

    override fun clientBuilder() = super.clientBuilder()
        .rateLimit(3)
        .addInterceptor(translatorInterceptor.apply { language = this@Snowmtl.settings })
        .addInterceptor(composeInterceptor.apply { language = this@Snowmtl.settings })

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)

        if (language.target == language.origin) {
            return
        }

        if (language.disableTranslationOptimization.not()) {
            SwitchPreferenceCompat(screen.context).apply {
                key = DISABLE_TRANSLATION_OPTIM_PREF
                title = "⚠ Disable translation optimization"
                summary = buildString {
                    append("Allows dialog boxes to be translated sequentially. ")
                    append("Avoids problems when loading some translated pages caused by the translator's text formatting. ")
                    append("Pages will load more slowly.")
                }
                setDefaultValue(false)
                setOnPreferenceChange { _, newValue ->
                    disableTranslationOptimization = newValue as Boolean
                    true
                }
            }.also(screen::addPreference)
        }
    }

    companion object {
        private const val DISABLE_TRANSLATION_OPTIM_PREF = "disableTranslationOptimizationPref"
    }
}
