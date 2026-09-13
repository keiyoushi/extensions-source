package eu.kanade.tachiyomi.extension.pt.mangastop

import eu.kanade.tachiyomi.multisrc.mangathemesia.ClientHintsInterceptor
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.rateLimit
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class MangaStop : MangaThemesia() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(acceptHeaderInterceptor())
        addCookie("wpmanga-ada" to "1")
        addInterceptor(ClientHintsInterceptor())
        rateLimit(2)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        set("Sec-Fetch-Dest", "document")
        set("Sec-Fetch-Mode", "navigate")
        set("Sec-Fetch-Site", "none")
        set("Sec-Fetch-User", "?1")
        set("Upgrade-Insecure-Requests", "1")
    }

    override fun pageListParse(document: Document) = super.pageListParse(document)
        .filterNot { it.imageUrl?.contains("mihon", true) == true }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = super.getFilterList(data).filterNot { it is AuthorFilter || it is YearFilter }
        return FilterList(filters)
    }
}
