package eu.kanade.tachiyomi.extension.id.manhwadesu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaDesu : MangaThemesia(
    "ManhwaDesu",
    "https://manhwadesu.cc",
    "id",
    "/komik",
    SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override fun Element.imgAttr(): String {
        attributes()
            .find { it.key.endsWith("original-src") }
            ?.let { return absUrl(it.key) }

        return when {
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("data-src") -> attr("abs:data-src")
            else -> attr("abs:src")
        }
    }
}
