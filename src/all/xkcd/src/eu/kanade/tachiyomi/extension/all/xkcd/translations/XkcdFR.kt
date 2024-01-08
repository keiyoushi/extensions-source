package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class XkcdFR : Xkcd("https://xkcd.lapin.org", "fr") {
    override val archive = "/tous-episodes.php"

    override val synopsis =
        "Un webcomic sarcastique qui parle de romance, de maths et de langage."

    override val chapterListSelector = "#content .s > a:not(:last-of-type)"

    override val imageSelector = "#content .s"

    override fun String.numbered(number: Any) = "$number. $this"

    override fun chapterListParse(response: Response) =
        response.asJsoup().select(chapterListSelector).map {
            SChapter.create().apply {
                url = "/" + it.attr("href")
                val number = url.substringAfter('=')
                name = it.text().numbered(number)
                chapter_number = number.toFloat()
                // no dates available
                date_upload = 0L
            }
        }

    override fun pageListParse(response: Response) =
        response.asJsoup().selectFirst(imageSelector)!!.let {
            // no interactive comics here
            val img = it.child(2).child(0).child(0)

            // create a text image for the alt text
            val text = wordWrap(it.child(0).text(), img.attr("alt"))

            listOf(Page(0, "", img.attr("abs:src")), Page(1, "", text.image()))
        }

    override val interactiveText: String
        get() = throw UnsupportedOperationException("Not used")
}
