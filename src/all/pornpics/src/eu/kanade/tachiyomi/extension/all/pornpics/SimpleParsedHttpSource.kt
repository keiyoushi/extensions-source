package eu.kanade.tachiyomi.extension.all.pornpics

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class SimpleParsedHttpSource : ParsedHttpSource() {

    abstract fun simpleMangaSelector(): String

    abstract fun simpleMangaFromElement(element: Element): SManga

    abstract fun simpleMangaParse(response: Response): MangasPage

    abstract fun simpleNextPageSelector(): String?

    // region popular
    override fun popularMangaSelector() = simpleMangaSelector()
    override fun popularMangaNextPageSelector() = simpleNextPageSelector()
    override fun popularMangaParse(response: Response) = simpleMangaParse(response)
    override fun popularMangaFromElement(element: Element) = simpleMangaFromElement(element)
    // endregion

    // region last
    override fun latestUpdatesSelector() = simpleMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = simpleMangaFromElement(element)
    override fun latestUpdatesParse(response: Response) = simpleMangaParse(response)
    override fun latestUpdatesNextPageSelector() = simpleNextPageSelector()
    // endregion

    // region search
    override fun searchMangaSelector() = simpleMangaSelector()
    override fun searchMangaFromElement(element: Element) = simpleMangaFromElement(element)
    override fun searchMangaParse(response: Response) = simpleMangaParse(response)
    override fun searchMangaNextPageSelector() = simpleNextPageSelector()
    // endregion

    override fun chapterListSelector() = simpleMangaSelector()
    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }
    // endregion
}
