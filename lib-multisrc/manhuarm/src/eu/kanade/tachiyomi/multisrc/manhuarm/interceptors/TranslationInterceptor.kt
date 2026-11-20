package eu.kanade.tachiyomi.multisrc.manhuarm.interceptors

import eu.kanade.tachiyomi.multisrc.manhuarm.Language
import eu.kanade.tachiyomi.multisrc.manhuarm.PageFragment
import eu.kanade.tachiyomi.multisrc.manhuarm.pageFragment
import eu.kanade.tachiyomi.multisrc.manhuarm.translator.TranslatorEngine
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class TranslationInterceptor(
    private val settings: () -> Language,
    private val translator: () -> TranslatorEngine,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val language = settings()
        val page = request.url.pageFragment()

        if (page == null || language.disableTranslator || language.target == language.origin) {
            return chain.proceed(request)
        }

        val engine = translator()
        val translated = runBlocking(Dispatchers.IO) {
            page.dialogues.map { dialog ->
                async {
                    dialog.copy(
                        textByLanguage = mapOf("text" to engine.translate(language.origin, language.target, dialog.text)),
                    )
                }
            }.awaitAll()
        }

        val url = request.url.newBuilder()
            .fragment(PageFragment(page.chapter, page.index, translated).toJsonString())
            .build()

        return chain.proceed(request.newBuilder().url(url).build())
    }
}
