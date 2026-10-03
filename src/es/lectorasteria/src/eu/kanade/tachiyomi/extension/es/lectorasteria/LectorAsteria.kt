package eu.kanade.tachiyomi.extension.es.lectorasteria

import eu.kanade.tachiyomi.multisrc.moonlighttl.MoonlightTL
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class LectorAsteria : MoonlightTL() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun pageListParse(document: Document): List<Page> = document.select("main > div > img.block").mapIndexed { i, element ->
        Page(i, imageUrl = element.attr("abs:src"))
    }
}
