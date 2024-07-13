package eu.kanade.tachiyomi.extension.id.manhwadesu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import org.jsoup.nodes.Element

class ManhwaDesu : MangaThemesia("ManhwaDesu", "https://manhwadesu.cc", "id", "/komik") {

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(4,2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("referer", "$baseUrl/")

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
