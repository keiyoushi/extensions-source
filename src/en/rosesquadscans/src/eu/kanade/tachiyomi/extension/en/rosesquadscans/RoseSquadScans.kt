package eu.kanade.tachiyomi.extension.en.rosesquadscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RoseSquadScans : Madara("Rose Squad Scans", "https://rosesquadscans.aishiteru.org", "en") {

    override val client = super.client.newBuilder()
        .addInterceptor(::authWarningIntercept)
        .rateLimit(1, 2)
        .build()

    override val useNewChapterEndpoint = true

    private fun authWarningIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.toString().contains("wp-login.php")) {
            response.close()
            throw IOException("It's necessary to login via WebView to access this source.")
        }

        return response
    }
}
