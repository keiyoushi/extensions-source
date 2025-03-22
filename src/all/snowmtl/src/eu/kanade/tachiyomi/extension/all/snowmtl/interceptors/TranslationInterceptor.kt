package eu.kanade.tachiyomi.extension.all.snowmtl.interceptors

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.extension.all.snowmtl.LanguageSetting
import eu.kanade.tachiyomi.extension.all.snowmtl.translator.TranslatorEngine
import eu.kanade.tachiyomi.multisrc.machinetranslations.Dialog
import eu.kanade.tachiyomi.multisrc.machinetranslations.MachineTranslations.Companion.PAGE_REGEX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

@RequiresApi(Build.VERSION_CODES.O)
class TranslationInterceptor(
    var language: LanguageSetting,
    private val translator: TranslatorEngine,
) : Interceptor {

    private val json: Json by injectLazy()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (PAGE_REGEX.containsMatchIn(url).not() || language.target == language.origin) {
            return chain.proceed(request)
        }

        val dialogues = request.url.fragment?.parseAs<List<Dialog>>()
            ?: return chain.proceed(request)

        val translated = runBlocking(Dispatchers.IO) {
            dialogues.map { dialog ->
                async {
                    dialog.replaceText(
                        translator.translate(language.origin, language.target, dialog.text),
                    )
                }
            }.awaitAll()
        }

        val newRequest = request.newBuilder()
            .url("${url.substringBeforeLast("#")}#${json.encodeToString(translated)}")
            .build()

        return chain.proceed(newRequest)
    }

    private fun Dialog.replaceText(value: String) = this.copy(
        textByLanguage = mutableMapOf(
            "text" to value,
        ),
    )

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }
}
