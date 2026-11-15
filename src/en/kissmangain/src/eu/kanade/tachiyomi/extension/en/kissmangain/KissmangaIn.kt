package eu.kanade.tachiyomi.extension.en.kissmangain

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class KissmangaIn : Madara() {
    override val mangaSubString = "kissmanga"

    override val chapterMode = ChapterMode.MangaAjax

    override fun imageFromElement(element: Element): String? = super.imageFromElement(element)?.trim()
}
