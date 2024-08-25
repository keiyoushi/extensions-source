package eu.kanade.tachiyomi.extension.en.luascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup

class LuaScans : MangaThemesia(
    "Lua Scans",
    "https://luacomic.net",
    "en",
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::wafffCookieInterceptor)
        .rateLimit(2)
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
