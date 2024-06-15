package eu.kanade.tachiyomi.extension.pt.atemporal

import android.webkit.CookieManager
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class Atemporal : Madara(
    "Atemporal",
    "https://atemporal.cloud",
    "pt-BR",
    dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val client = network.cloudflareClient.newBuilder()
        // Force visited=true cookie so we can use search
        // Implementation adapted from MangaFox
        .cookieJar(
            object : CookieJar {
                private val cookieManager by lazy { CookieManager.getInstance() }

                init {
                    cookieManager.setCookie(baseUrl.toHttpUrl().host, "visited=true")
                }

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val urlString = url.toString()
                    cookies.forEach { cookieManager.setCookie(urlString, it.toString()) }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cookies = cookieManager.getCookie(url.toString())

                    return if (cookies != null && cookies.isNotEmpty()) {
                        cookies.split(";").mapNotNull {
                            Cookie.parse(url, it)
                        }
                    } else {
                        emptyList()
                    }
                }
            },
        )
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
}
