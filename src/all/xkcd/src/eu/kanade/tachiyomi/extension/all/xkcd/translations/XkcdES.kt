package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class XkcdES : Xkcd("https://es.xkcd.com", "es") {
    // archive url is same as EN

    override val synopsis =
        "Un webcómic sobre romance, sarcasmo, mates y lenguaje."

    // Google translated, sorry
    override val interactiveText =
        "Para experimentar la versión interactiva de este cómic, ábralo en WebView/navegador."

    override val chapterListSelector = ".archive-entry > a"

    override val imageSelector = "#middleContent .strip"

    override fun chapterListParse(response: Response): List<SChapter> {
        val englishArchive = getComicDateMappingFromEnglishArchive()

        // hardcoded override for Spanish comic that maps to wrong English comic
        // Spanish "geografia" (1403) is actually English 1472 from 2015-01-12
        val spanishOverrides = mapOf(
            "/strips/geografia/" to 1472,
        )

        // reverse mapping, Spanish archive actually gives date but not comic number
        val dateToNumber = englishArchive.entries.associate { (number, date) ->
            val parts = date.split("-")
            val normalizedDate = "${parts[0]}-${parts[1].padStart(2, '0')}-${parts[2].padStart(2, '0')}"
            normalizedDate to number
        }

        return response.asJsoup().select(".archive-entry").mapNotNull { entry ->
            val link = entry.selectFirst("a") ?: return@mapNotNull null
            val title = link.text()

            val timeElement = entry.selectFirst("time") ?: return@mapNotNull null
            val datePart = timeElement.text()

            // check for hardcoded override first
            val urlPath = link.attr("abs:href").substringAfter(baseUrl)
            val comicNumber = spanishOverrides[urlPath] ?: dateToNumber[datePart] ?: return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(link.attr("abs:href"))
                name = chapterTitleFormatter(comicNumber, title)
                chapter_number = comicNumber.toFloat()
                date_upload = datePart.timestamp()
            }
        }
    }
}
