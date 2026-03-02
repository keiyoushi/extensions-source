package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element

class Siikomik :
    Madara(
        "Siikomik",
        "https://siikomik.net",
        "id",
    ) {
    override val versionId = 3

    override val mangaSubString = "komik"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        if (element.hasClass("premium") || element.hasClass("premium-block")) {
            name = "🔒 $name"
        }
    }
}
