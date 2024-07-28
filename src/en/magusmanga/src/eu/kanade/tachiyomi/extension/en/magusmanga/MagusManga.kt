package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MagusManga : MangaThemesiaAlt(
    "Magus Manga",
    "https://recipeslik.online",
    "en",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("en")),
) {
    override val id = 7792477462646075400

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::wafffCookieInterceptor)
        .rateLimit(1, 1, TimeUnit.SECONDS)
        .build()

    private fun wafffCookieInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            response.request.url.toString(),
        )

        return if (document.selectFirst("script:containsData(wafff)") != null) {
            val script = document.selectFirst("script:containsData(wafff)")!!.data()

            val cookie = waffRegex.find(script)?.groups?.get("waff")?.value
                ?.let { Cookie.parse(request.url, it) }

            client.cookieJar.saveFromResponse(
                request.url,
                listOfNotNull(cookie),
            )

            response.close()

            chain.proceed(request)
        } else {
            response
        }
    }

    private val waffRegex = Regex("""document\.cookie\s*=\s*['"](?<waff>.*)['"]""")
}
