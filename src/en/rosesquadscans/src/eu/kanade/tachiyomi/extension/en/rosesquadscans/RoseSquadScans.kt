package eu.kanade.tachiyomi.extension.en.rosesquadscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class RoseSquadScans : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM.dd.yyyy", Locale.US)
    override val chapterMode = ChapterMode.MangaAjax

    override val supportsFilterFetching = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::authWarningIntercept)
        rateLimit(1, 2.seconds)
    }

    private fun authWarningIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.toString().contains("wp-login.php")) {
            response.close()
            throw IOException("It's necessary to login via WebView to access this source.")
        }

        return response
    }
}
