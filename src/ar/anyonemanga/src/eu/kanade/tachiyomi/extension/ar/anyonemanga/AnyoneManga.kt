package eu.kanade.tachiyomi.extension.ar.anyonemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class AnyoneManga : Madara() {
    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT)
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun imageFromElement(element: Element): String? = element.attr("abs:data-encrypted-src")
}
