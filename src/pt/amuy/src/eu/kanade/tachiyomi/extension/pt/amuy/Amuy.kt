package eu.kanade.tachiyomi.extension.pt.amuy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Amuy : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override fun pageListParse(document: Document): List<Page> {
        val chapterProtector = document.selectFirst(chapterProtectorSelector)

        if (chapterProtector == null) {
            launchIO { countViews(document) }

            val pageElements = document.select(
                "div.page-break img, li.blocks-gallery-item img, .reading-content .text-left:not(:has(.blocks-gallery-item)) img",
            )

            return pageElements.mapIndexed { index, element ->
                Page(index, document.location(), imageFromElement(element))
            }
        }

        return super.pageListParse(document)
    }
}
