package eu.kanade.tachiyomi.extension.id.manhwadesu

import eu.kanade.tachiyomi.multisrc.mangathemesia.ClientHintsInterceptor
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class ManhwaDesu : MangaThemesia() {
    override val mangaUrlDirectory = "/komik"

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ClientHintsInterceptor())
        rateLimit(4)
    }

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
