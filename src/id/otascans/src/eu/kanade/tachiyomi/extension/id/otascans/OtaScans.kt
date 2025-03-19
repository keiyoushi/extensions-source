package eu.kanade.tachiyomi.extension.id.otascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class OtaScans : MangaThemesia(
    "Ota Scans",
    "https://yurilabs.my.id",
    "id",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).takeIf { it.isNotEmpty() }
            ?: throw Exception("Maybe this content needs a password. Open in WebView")
    }
}
