package eu.kanade.tachiyomi.extension.en.gedecomix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class GEDEComix : Madara() {
    override val mangaDetailsSelectorThumbnail = "${super.mangaDetailsSelectorThumbnail}:not([data-eio])"
    override val mangaSubString = "porncomic"
    override val chapterMode = ChapterMode.MangaAjax

    override fun archiveManga(element: Element, id: String): SManga? = super.archiveManga(element, id)?.apply {
        element.selectFirst("img:not([data-eio])")?.let {
            thumbnail_url = imageFromElement(it)
        }
    }
}
