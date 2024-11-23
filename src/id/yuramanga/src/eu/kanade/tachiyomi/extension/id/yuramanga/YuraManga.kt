package eu.kanade.tachiyomi.extension.id.yuramanga

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class YuraManga : ZManga(
    "YuraManga",
    "https://www.yuramanga.my.id",
    "id",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    // Moved from Madara to ZManga
    override val versionId = 3

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.request.url.pathSegments.contains("login")) {
                throw IOException("Please log in to the WebView to continue")
            }
            response
        }
        .build()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-area img.lazyload").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src"))
        }
    }
}
