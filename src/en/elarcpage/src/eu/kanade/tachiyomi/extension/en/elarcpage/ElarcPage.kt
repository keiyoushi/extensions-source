package eu.kanade.tachiyomi.extension.en.elarcpage

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException

class ElarcPage : MangaThemesia(
    "Elarc Toon",
    "https://elarctoons.com",
    "en",
) {
    override val id = 5482125641807211052

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::dynamicUrlInterceptor)
        .build()

    private var dynamicUrlUpdated: Long = 0
    private val dynamicUrlValidity: Long = 10 * 60 // 10 minutes

    private fun dynamicUrlInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val timeNow = System.currentTimeMillis() / 1000

        // Check if request requires an up-to-date URL
        if (request.url.pathSegments[0] == mangaUrlDirectory.substring(1)) {
            // Force update URL if required
            if (timeNow - dynamicUrlUpdated > dynamicUrlValidity) {
                client.newCall(GET(baseUrl)).execute()
                if (timeNow - dynamicUrlUpdated > dynamicUrlValidity) {
                    throw IOException("Failed to update dynamic url")
                }
            }

            if (request.url.pathSegments[0] != mangaUrlDirectory.substring(1)) {
                // Need to rewrite URL

                val newUrl = request.url.newBuilder()
                    .setPathSegment(0, mangaUrlDirectory.substring(1))
                    .build()

                val newRequest = request.newBuilder()
                    .url(newUrl)
                    .build()

                return chain.proceed(newRequest)
            }
        }

        // Always update URL
        val response = chain.proceed(request)
        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            request.url.toString(),
        )

        document.select("#menu-item-14 > a, a:contains(All Series), #main-menu a, .mm a")
            .reversed()
            .map { it.attr("href") }
            .lastOrNull { it.length >= 2 && it[0] == '/' }
            ?.let {
                setMangaUrlDirectory(it)
                dynamicUrlUpdated = timeNow
            }

        return response
    }

    private fun setMangaUrlDirectory(mangaUrlDirectory: String) {
        try {
            // this is fine
            val field = this.javaClass.superclass.getDeclaredField("mangaUrlDirectory")
            field.isAccessible = true
            field.set(this, mangaUrlDirectory)
        } catch (_: Exception) {
        }
    }
}
