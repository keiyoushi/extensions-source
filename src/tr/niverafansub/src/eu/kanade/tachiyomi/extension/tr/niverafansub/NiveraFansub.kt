package eu.kanade.tachiyomi.extension.tr.niverafansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NiveraFansub : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val res = chain.proceed(chain.request())
        if ("bilgilendirme" in res.request.url.pathSegments) {
            throw IOException("Okumak için WebView üzerinden giriş yapın")
        }
        res
    }

    override val chapterUrlSelector = "li > a"
}
