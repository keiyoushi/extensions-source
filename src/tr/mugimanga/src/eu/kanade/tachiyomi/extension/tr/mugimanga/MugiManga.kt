package eu.kanade.tachiyomi.extension.tr.mugimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale

class MugiManga : Madara(
    "Mugi Manga",
    "https://mugimanga.com",
    "tr",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val supportsLatest = false

    override val useNewChapterEndpoint = true

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).takeIf { it.isNotEmpty() }
            ?: throw Exception("WebView'de oturum açmanız gerekebilir")
    }
}
