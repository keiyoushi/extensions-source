package eu.kanade.tachiyomi.extension.ar.yonabar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class YonaBar : Madara() {
    override val mangaSubString = "yaoi"

    override val pageListParseSelector = ".reading-content img:not(#image-0\\.0)"

    override fun parsePages(document: Document) = super.parsePages(document)
        .mapNotNull { page ->
            page.apply {
                imageUrl = imageUrl!!
                    .replaceFirst("medium1", "medium1xf")
                    .replaceFirst("medium2", "medium2x")
            }
        }
}
