package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import okhttp3.Response

class XkcdFR : Xkcd("https://xkcd.lapin.org", "fr") {
    override val archive = "/tous-episodes.php"

    override val synopsis =
        "Un webcomic sarcastique qui parle de romance, de maths et de langage."

    override val chapterListSelector = "#content .s > a:not(:last-of-type)"

    override val imageSelector = "#content .s"

    override fun chapterListParse(response: Response): List<SChapter> {
        val englishDates = getComicDateMappingFromEnglishArchive()

        val chapters = response.asJsoup().select(chapterListSelector).map {
            SChapter.create().apply {
                url = "/" + it.attr("href")
                val comicNumber = url.substringAfter('=').toIntOrNull()

                name = chapterTitleFormatter(comicNumber ?: 0, it.text())
                chapter_number = comicNumber?.toFloat() ?: 0f

                // use English publication date instead of translation date
                date_upload = if (comicNumber != null && englishDates.containsKey(comicNumber)) {
                    val dateStr = englishDates[comicNumber]!!
                    dateStr.timestamp()
                } else {
                    0L
                }
            }
        }

        // French archive lists oldest first
        return chapters.reversed()
    }

    override fun extractImageFromContainer(container: org.jsoup.nodes.Element): org.jsoup.nodes.Element? = container.selectFirst("img[src^='strips/']")

    override fun pageListParse(response: Response): List<Page> {
        val container = response.asJsoup().selectFirst(imageSelector)
            ?: error(interactiveText)

        val img = container.selectFirst("img[src^='strips/']") ?: error(interactiveText)

        // Get alt text - try from first child, fallback to img alt
        val altText = container.selectFirst("div:not(.buttons)")?.text()?.takeIf { it.isNotBlank() }
            ?: img.attr("alt")

        // create a text image for the alt text
        val text = TextInterceptorHelper.createUrl(altText, img.attr("title"))

        return listOf(Page(0, "", img.attr("abs:src")), Page(1, "", text))
    }

    override val interactiveText: String
        get() = throw UnsupportedOperationException()
}
