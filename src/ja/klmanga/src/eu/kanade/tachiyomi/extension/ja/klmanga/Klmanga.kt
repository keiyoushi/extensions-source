package eu.kanade.tachiyomi.extension.ja.klmanga

import eu.kanade.tachiyomi.multisrc.zing.Zing
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class Klmanga : Zing() {
    override val mangaSubString = "manga-raw"
    override val descriptionSelector = ".z-single-mg .content-text"

    // The newest chapter's link also holds a "New" badge
    override fun chapterName(link: Element) = link.selectFirst("span")!!.text()
}
