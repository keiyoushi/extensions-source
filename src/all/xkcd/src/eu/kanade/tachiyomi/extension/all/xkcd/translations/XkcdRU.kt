package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class XkcdRU : Xkcd("https://xkcd.ru", "ru") {
    override val archive = "/img"

    override val creator = "Рэндел Манро"

    override val synopsis = "о романтике, сарказме, математике и языке"

    override val altTextUrl = super.altTextUrl.replace("museo", "noto")

    override val chapterListSelector = ".main > a"

    override val imageSelector = ".main"

    override fun String.numbered(number: Any) = "$number: $this"

    override fun chapterListParse(response: Response) =
        response.asJsoup().select(chapterListSelector).map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.child(0).attr("title")
                chapter_number = url.removeSurrounding("/").toFloat()
                // no dates available
                date_upload = 0L
            }
        }

    override fun pageListParse(response: Response) =
        response.asJsoup().selectFirst(imageSelector)!!.let {
            // no interactive comics here
            val img = it.child(5).child(0)

            // create a text image for the alt text
            val text = wordWrap(img.attr("alt"), it.child(7).text())

            listOf(Page(0, "", img.attr("abs:src")), Page(1, "", text.image()))
        }

    override val interactiveText: String
        get() = throw UnsupportedOperationException("Not used")
}
