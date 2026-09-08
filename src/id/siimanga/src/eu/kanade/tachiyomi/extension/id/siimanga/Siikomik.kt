package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class Siikomik : Madara() {

    override val mangaSubString = "komik"
    override val chapterMode = ChapterMode.MangaAjax

    override fun chapterFromElement(element: Element, mangaPath: String): SChapter? = super.chapterFromElement(element, mangaPath)?.apply {
        if (element.hasClass("premium") || element.hasClass("premium-block")) {
            name = "🔒 $name"
        }
    }
}
