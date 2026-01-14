package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val englishDates = try {
            getComicDateMappingFromEnglishArchive()
        } catch (e: Exception) {
            emptyMap()
        }

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

    override fun extractImageFromContainer(container: org.jsoup.nodes.Element): org.jsoup.nodes.Element? {
        return try {
            container.child(2).child(0).child(0)
        } catch (e: Exception) {
            container.selectFirst("img")
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val container = response.asJsoup().selectFirst(imageSelector)
            ?: error(interactiveText)

        // Try to find the image - French xkcd has a specific structure
        val img = try {
            container.child(2).child(0).child(0)
        } catch (e: Exception) {
            // Fallback: look for any img tag in the container
            container.selectFirst("img") ?: error(interactiveText)
        }

        // Get alt text - try from first child, fallback to img alt
        val altText = try {
            container.child(0).text()
        } catch (e: Exception) {
            img.attr("alt")
        }

        // create a text image for the alt text
        val text = TextInterceptorHelper.createUrl(altText, img.attr("alt"))

        return listOf(Page(0, "", img.attr("abs:src")), Page(1, "", text))
    }

    override val interactiveText: String
        get() = throw UnsupportedOperationException()
}
