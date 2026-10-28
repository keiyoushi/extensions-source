package eu.kanade.tachiyomi.extension.es.mangaromance

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import keiyoushi.annotation.Source
import keiyoushi.utils.sha256
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaRomance : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale("es"))
    override val chapterMode = ChapterMode.MangaAjax

    override fun processThumbnail(url: String?, fromSearch: Boolean) = url?.replace(THUMBNAIL_SIZE_REGEX, "$1")

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::jsChallengeInterceptor)

    // Hostinger CDN browser check
    private fun jsChallengeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookie = challengeCookie(request.url)
        val response = chain.proceed(request)
        if (response.code != 403 || response.header("Server") != "hcdn") {
            return response
        }
        response.close()

        solveChallenge(chain, request.url, cookie)
        return chain.proceed(request)
    }

    @Synchronized
    private fun solveChallenge(chain: Interceptor.Chain, url: HttpUrl, failedCookie: String?) {
        if (challengeCookie(url) != failedCookie) return

        val script = chain.proceed(GET("$baseUrl/hcdn-cgi/jschallenge", headers)).body.string()
        val token = TOKEN_REGEX.find(script)?.groupValues?.get(1)
            ?: throw IOException("Failed to fetch challenge token")

        val body = FormBody.Builder()
            .add("challenge", token.sha256().toHexString())
            .build()
        chain.proceed(POST("$baseUrl/hcdn-cgi/jschallenge-validate", headers, body)).use {
            if (!it.isSuccessful) throw IOException("Failed to pass browser check")
        }
    }

    private fun challengeCookie(url: HttpUrl) = client.cookieJar.loadForRequest(url).firstOrNull { it.name == "hcdn" }?.value

    companion object {
        private val THUMBNAIL_SIZE_REGEX = """-\d+x\d+(\.\w+)$""".toRegex()
        private val TOKEN_REGEX = """const cjs = '([^']+)'""".toRegex()
    }
}
