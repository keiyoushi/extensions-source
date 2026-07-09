package eu.kanade.tachiyomi.extension.pt.cerisescans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CeriseScan : Madara() {
    override val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)

    override val client = super.client.newBuilder()
        .rateLimit(3, 2.seconds)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.selectFirst(".page-break script")?.data()
            ?.let { PAGE_REGEX.find(it)?.groupValues?.last() }
            ?: return emptyList()
        return pages.parseAs<List<String>>().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    companion object {
        private val PAGE_REGEX = """content:\s+(\[[\s\S*]+\])""".toRegex()
    }
}
