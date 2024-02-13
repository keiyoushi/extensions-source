package eu.kanade.tachiyomi.extension.th.cattranslator

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class CatTranslator : Madara(
    "CAT-translator",
    "https://cats-translator.com/manga",
    "th",
) {
    private fun parseMangaFromElement(element: Element, isSearch: Boolean): SManga {
        val manga = SManga.create()

        with(element) {
            select(if (isSearch) "div.post-title a" else popularMangaUrlSelector).first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.url = manga.url.removePrefix("/manga")
                manga.title = it.ownText()
            }

            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun popularMangaFromElement(element: Element) = parseMangaFromElement(element, false)

    override fun searchMangaFromElement(element: Element) = parseMangaFromElement(element, true)
}
