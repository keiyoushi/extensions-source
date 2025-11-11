package eu.kanade.tachiyomi.extension.all.baobua

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class SimpleParsedHttpSource : ParsedHttpSource() {

    abstract fun simpleMangaSelector(): String

    abstract fun simpleMangaFromElement(element: Element): SManga

    abstract fun simpleNextPageSelector(): String?

    // region popular
    override fun popularMangaSelector() = simpleMangaSelector()
    override fun popularMangaNextPageSelector() = simpleNextPageSelector()
    override fun popularMangaFromElement(element: Element) = simpleMangaFromElement(element)
    // endregion

    // region last
    override fun latestUpdatesSelector() =
        if (supportsLatest) simpleMangaSelector() else throw throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) =
        if (supportsLatest) simpleMangaFromElement(element) else throw throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() =
        if (supportsLatest) simpleNextPageSelector() else throw throw UnsupportedOperationException()
    // endregion

    // region search
    override fun searchMangaSelector() = simpleMangaSelector()
    override fun searchMangaFromElement(element: Element) = simpleMangaFromElement(element)
    override fun searchMangaNextPageSelector() = simpleNextPageSelector()
    // endregion

    override fun chapterListSelector() = simpleMangaSelector()
    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }
    // endregion
}
