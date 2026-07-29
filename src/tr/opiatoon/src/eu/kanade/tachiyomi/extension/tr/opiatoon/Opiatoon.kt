package eu.kanade.tachiyomi.extension.tr.opiatoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

@Source
abstract class Opiatoon : Madara() {
    override val chapterDateFormat = DateTimeFormatterBuilder()
        .appendPattern("d MMMM")
        .parseDefaulting(ChronoField.YEAR, LocalDate.now().year.toLong())
        .toFormatter(Locale.forLanguageTag("tr"))
    override val chapterUrlSelector = "li > a"
    override val chapterMode = ChapterMode.MangaAjax

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::loginCheckInterceptor)

    private fun loginCheckInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.encodedPath.startsWith("/giris-yapmalisiniz")) {
            response.close()
            throw IOException("Okumak için WebView üzerinden giriş yapın")
        }

        return response
    }
}
