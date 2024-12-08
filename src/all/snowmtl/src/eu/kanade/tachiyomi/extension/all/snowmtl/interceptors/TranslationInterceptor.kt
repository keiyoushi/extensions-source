package eu.kanade.tachiyomi.extension.all.snowmtl.interceptors

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.extension.all.snowmtl.Dialog
import eu.kanade.tachiyomi.extension.all.snowmtl.Snowmtl.Companion.PAGE_REGEX
import eu.kanade.tachiyomi.extension.all.snowmtl.Source
import eu.kanade.tachiyomi.extension.all.snowmtl.enginetranslator.TranslatorEngine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

@RequiresApi(Build.VERSION_CODES.O)
class TranslationInterceptor(
    private val source: Source,
    private val translator: TranslatorEngine,
) : Interceptor {

    private val json: Json by injectLazy()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (PAGE_REGEX.containsMatchIn(url).not() || source.target == source.origin) {
            return chain.proceed(request)
        }

        val dialogues = request.url.fragment?.parseAs<List<Dialog>>()
            ?: return chain.proceed(request)

        val translated = dialogues.map {
            it.copy(text = translator.translate(source.origin, source.target, it.text))
        }

        val newRequest = request.newBuilder()
            .url("${url.substringBeforeLast("#")}#${json.encodeToString(translated)}")
            .build()

        return chain.proceed(newRequest)
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromStream(body.byteStream())
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }
}
