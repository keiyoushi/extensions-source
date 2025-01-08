package eu.kanade.tachiyomi.extension.all.snowmtl

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.extension.all.snowmtl.interceptors.TranslationInterceptor
import eu.kanade.tachiyomi.extension.all.snowmtl.translator.BingTranslator
import eu.kanade.tachiyomi.extension.all.snowmtl.translator.TranslatorEngine
import eu.kanade.tachiyomi.multisrc.machinetranslations.Language
import eu.kanade.tachiyomi.multisrc.machinetranslations.MachineTranslations
import eu.kanade.tachiyomi.multisrc.machinetranslations.interceptors.ComposedImageInterceptor
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.O)
class Snowmtl(
    language: Language,
) : MachineTranslations(
    name = "Snow Machine Translations",
    baseUrl = "https://snowmtl.ru",
    language,
) {
    override val lang = language.lang

    private val clientUtils = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    private val translator: TranslatorEngine = BingTranslator(clientUtils, headers)

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .readTimeout(2, TimeUnit.MINUTES)
        .addInterceptor(TranslationInterceptor(language, translator))
        .addInterceptor(ComposedImageInterceptor(baseUrl, language))
        .build()
}
