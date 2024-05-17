package eu.kanade.tachiyomi.extension.en.dragontea

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class DragonTea : Madara(
    "DragonTea",
    "https://dragontea.ink",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val mangaSubString = "novel"

    override val useNewChapterEndpoint = true

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".reading-content .page-break img").mapIndexed { index, element ->
            Page(index, document.location(), element.attr("abs:data-src"))
        }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            set("Referer", page.url)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }
}
