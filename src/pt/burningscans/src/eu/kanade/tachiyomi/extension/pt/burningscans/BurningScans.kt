package eu.kanade.tachiyomi.extension.pt.burningscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class BurningScans : Madara(
    "Burning Scans",
    "https://burningscans.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    override fun genresRequest(): Request {
        return GET("$baseUrl/?s=&post_type=wp-manga", headers)
    }
}
