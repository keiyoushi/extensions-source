package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import okhttp3.Response

class XkcdRU : Xkcd("https://xkcd.ru", "ru") {
    override val archive = "/img"

    override val creator = "Рэндел Манро"

    override val synopsis = "о романтике, сарказме, математике и языке"

    override val chapterListSelector = ".main > a"

    override val imageSelector = ".main"

    override fun chapterListParse(response: Response): List<SChapter> {
        val englishDates = getComicDateMappingFromEnglishArchive()

        return response.asJsoup().select(chapterListSelector).map {
            SChapter.create().apply {
                url = it.attr("href")
                val comicNumber = url.removeSurrounding("/").toIntOrNull()
                val title = it.child(0).attr("alt")

                name = chapterTitleFormatter(comicNumber ?: 0, title)
                chapter_number = comicNumber?.toFloat() ?: 0f

                // use English publication date instead of translation date
                date_upload = if (comicNumber != null && englishDates.containsKey(comicNumber)) {
                    englishDates[comicNumber]!!.timestamp()
                } else {
                    0L
                }
            }
        }
    }

    override fun extractImageFromContainer(container: org.jsoup.nodes.Element): org.jsoup.nodes.Element? = container.selectFirst("img[src*='/i/']")

    override fun pageListParse(response: Response) = response.asJsoup().selectFirst(imageSelector)!!.let { container ->
        // no interactive comics here
        val img = container.selectFirst("img[src*='/i/']")!!

        // create a text image for the alt text
        val description = container.selectFirst(".comics_text")?.text() ?: img.attr("alt")
        val text = TextInterceptorHelper.createUrl(img.attr("alt"), description)

        listOf(Page(0, "", img.attr("abs:src")), Page(1, "", text))
    }

    override val interactiveText: String
        get() = throw UnsupportedOperationException()
}
