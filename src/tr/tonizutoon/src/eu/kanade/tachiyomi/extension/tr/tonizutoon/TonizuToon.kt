package eu.kanade.tachiyomi.extension.tr.tonizutoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TonizuToon : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorTitle = "#manga-title"
    override val mangaDetailsSelectorAuthor = ".summary-heading:contains(Yazar) ~ .summary-content"
    override val mangaDetailsSelectorStatus = ".summary-heading:contains(Durum) ~ .summary-content"

    override fun OkHttpClient.Builder.configureClient() = addNetworkInterceptor(::loginCheckInterceptor)

    private fun loginCheckInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.request.url.encodedPath == "/giris-uyari/") {
            response.close()
            throw IOException("WebView'de oturum açarak erişin")
        }
        return response
    }
}
