package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class XkcdES : Xkcd("https://es.xkcd.com", "es") {
    override val synopsis =
        "Un webcómic sobre romance, sarcasmo, mates y lenguaje."

    // Google translated, sorry
    override val interactiveText =
        "Para experimentar la versión interactiva de este cómic, ábralo en WebView/navegador."

    override val chapterListSelector = "#archive-ul > ul > li > a"

    override val imageSelector = "#middleContent .strip"

    override fun chapterListParse(response: Response) =
        response.asJsoup().select(chapterListSelector).mapIndexed { idx, el ->
            SChapter.create().apply {
                name = el.text()
                // convert relative path to absolute
                url = el.attr("href").substring(2)
                // not accurate to the original ¯\_(ツ)_/¯
                chapter_number = idx + 1f
                // no dates available
                date_upload = 0L
            }
        }

    override fun String.numbered(number: Any) =
        throw UnsupportedOperationException("Not used")
}
