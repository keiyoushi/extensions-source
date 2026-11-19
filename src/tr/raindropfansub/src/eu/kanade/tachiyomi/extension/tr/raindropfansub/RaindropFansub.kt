package eu.kanade.tachiyomi.extension.tr.raindropfansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.io.IOException

@Source
abstract class RaindropFansub : MangaThemesia() {
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Tür) a"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::botCheckInterceptor)

    // Site cookies when the bot check was last served; LiteSpeed drops the IP after a few check pages
    @Volatile
    private var cookiesAtBotCheck: Set<String>? = null

    private fun botCheckInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val siteUrl = baseUrl.toHttpUrl()
        if (request.url.host != siteUrl.host) return chain.proceed(request)

        val cookies = client.cookieJar.loadForRequest(siteUrl).mapTo(HashSet()) { "${it.name}=${it.value}" }
        if (cookies == cookiesAtBotCheck) throw IOException(BOT_CHECK_MESSAGE)

        val response = chain.proceed(request)
        if (!response.isBotCheck()) return response

        response.close()
        cookiesAtBotCheck = cookies
        throw IOException(BOT_CHECK_MESSAGE)
    }

    // Both checks come back as 200 HTML: the host's "One moment" script page, then LiteSpeed's reCAPTCHA
    private fun Response.isBotCheck(): Boolean {
        if (header("Content-Type")?.startsWith("text/html") != true) return false
        val head = peekBody(BOT_CHECK_PEEK).string()
        return BOT_CHECK_MARKERS.any { it in head }
    }

    override fun chapterListParse(document: Document): List<SChapter> {
        // "İlk Bölüm" points to the first chapter, but is often wrong on the site
        // We look at "Son Bölüm" to find the last chapter and sort accordingly
        val chapters = super.chapterListParse(document)

        val lastChapterUrl = document
            .selectFirst("a:has(.epcurlast)")
            ?.absUrl("href")
            ?.let {
                val dummyChapter = SChapter.create()
                dummyChapter.setUrlWithoutDomain(it)
                dummyChapter.url
            }

        return if (lastChapterUrl == chapters.firstOrNull()?.url) chapters else chapters.reversed()
    }

    companion object {
        private const val BOT_CHECK_PEEK = 4096L
        private val BOT_CHECK_MARKERS = listOf("/.lsrecap/recaptcha", "<title>One moment, please...</title>")
        private const val BOT_CHECK_MESSAGE = "Open in WebView to pass the site's verification"
    }
}
