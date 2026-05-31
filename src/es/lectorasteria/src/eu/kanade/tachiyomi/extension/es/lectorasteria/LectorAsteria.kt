package eu.kanade.tachiyomi.extension.es.lectorasteria

import eu.kanade.tachiyomi.multisrc.moonlighttl.MoonlightTL
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

class LectorAsteria :
    MoonlightTL(
        "Lector Asteria",
        "https://lectorasteria.com",
        "es",
    ) {

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("main > div > img.block").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }
}
