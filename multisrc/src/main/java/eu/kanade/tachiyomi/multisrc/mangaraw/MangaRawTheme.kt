package eu.kanade.tachiyomi.multisrc.mangaraw

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator

abstract class MangaRawTheme(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "ja",
) : ParsedHttpSource() {

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override val client = network.cloudflareClient

    protected abstract fun String.sanitizeTitle(): String

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst(Evaluator.Tag("a"))!!.attr("href"))

        // Title could be missing. Uses URL as a last effort, ex: okaeri-alice-raw
        title = element.selectFirst(Evaluator.Tag("h3"))?.text()?.sanitizeTitle()
            ?: (baseUrl + url).toHttpUrl().pathSegments.first()

        thumbnail_url = element.selectFirst(Evaluator.Tag("img"))?.absUrl("data-src")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page", headers)
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    /** Other recommended manga must be removed. Make sure the last `<p>` is description. */
    protected abstract fun Document.getSanitizedDetails(): Element

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val root = document.getSanitizedDetails()
        thumbnail_url = root.selectFirst(Evaluator.Tag("img"))?.run {
            absUrl("data-src").ifEmpty { absUrl("src") }
        }
        description = root.select(Evaluator.Tag("p")).lastOrNull { it.text().isNotEmpty() }
            ?.run {
                select(Evaluator.Tag("br")).prepend("\\n")
                text().replace("\\n ", "\n")
            }
        genre = root.select(Evaluator.AttributeWithValueContaining("rel", "tag"))
            .flatMapTo(mutableSetOf()) { element ->
                val text = element.ownText()
                if (text.all { it.code < 128 }) return@flatMapTo listOf(text)
                text.split(' ', '.', '・', '。')
            }.joinToString()
    }

    protected abstract fun String.sanitizeChapter(): String

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text().sanitizeChapter()
    }

    protected abstract fun pageSelector(): Evaluator

    override fun pageListParse(document: Document): List<Page> {
        val imgSelector = Evaluator.Tag("img")
        return document.select(pageSelector()).mapIndexed { index, element ->
            Page(index, imageUrl = element.selectFirst(imgSelector)!!.attr("data-src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")
}
