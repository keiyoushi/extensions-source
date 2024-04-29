package eu.kanade.tachiyomi.extension.en.gedecomix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class GEDEComix : Madara(
    "GEDE Comix",
    "https://gedecomix.com",
    "en",
) {
    override val mangaDetailsSelectorThumbnail = "${super.mangaDetailsSelectorThumbnail}:not([data-eio])"

    override val useNewChapterEndpoint = true

    override val mangaSubString = "porncomic"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = super.popularMangaFromElement(element)
        return fixThumbnail(element, manga)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = super.searchMangaFromElement(element)
        return fixThumbnail(element, manga)
    }

    private fun fixThumbnail(element: Element, manga: SManga): SManga {
        element.selectFirst("img:not([data-eio])")?.also {
            manga.thumbnail_url = imageFromElement(it)
        }
        return manga
    }
}
