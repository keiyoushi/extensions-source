package eu.kanade.tachiyomi.extension.fr.mangahubfr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaHubFr : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)

    // Only display chapters which don't have Premium
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.vip-permission)"

    override val chapterUrlSuffix = ""

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        return document.select(pageListParseSelector)
            .mapIndexed { index, element ->
                // Had to add trim because of white space in source.
                val imageUrl = element.select("img").attr("abs:src").trim()
                Page(index, document.location(), imageUrl)
            }
    }
}
