package eu.kanade.tachiyomi.extension.pt.atemporal

import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class Atemporal : Madara(
    "Atemporal",
    "https://atemporal.cloud",
    "pt-BR",
    dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
) {
    // Cookie required for search
    private val cookieInterceptor = CookieInterceptor(baseUrl.toHttpUrl().host, "visited" to "true")

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(cookieInterceptor)
        .build()

    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
}
