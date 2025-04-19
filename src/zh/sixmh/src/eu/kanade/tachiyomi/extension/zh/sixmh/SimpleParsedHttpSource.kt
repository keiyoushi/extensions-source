package eu.kanade.tachiyomi.extension.zh.sixmh

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class SimpleParsedHttpSource : ParsedHttpSource() {

    abstract fun simpleMangaSelector(): String
    abstract fun simpleMangaFromElement(element: Element): SManga
    abstract fun simpleNextPageSelector(): String?

    override fun popularMangaSelector() = simpleMangaSelector()
    override fun popularMangaFromElement(element: Element) = simpleMangaFromElement(element)
    override fun popularMangaNextPageSelector() = simpleNextPageSelector()

    override fun latestUpdatesSelector() = simpleMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = simpleMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = simpleNextPageSelector()

    override fun searchMangaSelector() = simpleMangaSelector()
    override fun searchMangaFromElement(element: Element) = simpleMangaFromElement(element)
    override fun searchMangaNextPageSelector() = simpleNextPageSelector()

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
}
