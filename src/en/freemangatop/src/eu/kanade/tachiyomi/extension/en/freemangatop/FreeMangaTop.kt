package eu.kanade.tachiyomi.extension.en.freemangatop

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class FreeMangaTop : Madara(
    "FreeMangaTop",
    "https://freemangatop.com",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
) {
    override val filterNonMangaItems = false

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            val descriptionElement = document.selectFirst(mangaDetailsSelectorDescription)
                ?: return@apply

            val elements = descriptionElement.select("> *")
                .drop(1)
                .takeWhile { it.tagName() != "ul" }

            val startIndices = listOf(
                elements.indexOfFirst { it.outerHtml() == "<h4>Summary:</h4>" },
                elements.indexOfFirst { it.outerHtml() == "<p>Summary</p>" },
                elements.indexOfFirst { it.outerHtml() == "<p><strong>Summary:&nbsp;</strong></p>" },
                elements.indexOfFirst { it.outerHtml() == "<p><strong>Summary</strong>:</p>" },
                elements.indexOfFirst { it.outerHtml() == "<h3>Description:</h3>" },
                elements.indexOfFirst { it.outerHtml().startsWith("<h3>Description about ") },
                elements.indexOfFirst { it.outerHtml().startsWith("<p>Description <strong>") },
                elements.indexOfFirst { it.outerHtml().startsWith("<p><strong>Reading ") },
                elements.indexOfFirst { it.outerHtml().startsWith("<p><strong>Summary of ") },
                elements.indexOfFirst { it.outerHtml().startsWith("<h2>Description of ") },
            ).filter { it != -1 }

            val endIndices = listOf(
                elements.indexOfFirst { it.outerHtml() == "<h4>Alternative Name:</h4>" },
                elements.indexOfFirst { it.outerHtml().startsWith("<p>Welcome to <strong>") },
                elements.indexOfFirst { it.outerHtml().startsWith("<p>Welcome to MangaZone") },
                elements.indexOfFirst { it.outerHtml() == "<p><strong><u>Search for series of same genre(s)</u></strong></p>" },
                elements.indexOfFirst { it.outerHtml().startsWith("<p><strong>Associated Name") },
                elements.indexOfFirst { it.outerHtml() == "<p><strong>Alternative:</strong></p>" },
                elements.indexOfFirst { it.outerHtml() == "<p><strong>Review:</strong></p>" },
                elements.indexOfFirst { it.outerHtml() == "<h3>Associated Names:</h3>" },
                elements.indexOfFirst { it.outerHtml() == "<p>Maybe you like !</p>" },
                elements.indexOfFirst { it.outerHtml() == "<p>Recommend for you !</p>" },
                elements.indexOfFirst { it.outerHtml() == "<p>Associated Names:</p>" },
                elements.indexOfFirst { it.outerHtml() == "<p>Welcome to our Website</p>" },
                elements.indexOfFirst { it.outerHtml().startsWith("<p>Associated <b>") },
                elements.indexOfFirst { it.outerHtml().endsWith("</strong> name:</p>") },
                elements.indexOfFirst { it.outerHtml().endsWith("</strong> other name:</p>") },
            ).filter { it != -1 }

            val minIndex = startIndices.maxOrNull()?.plus(1) ?: 0
            val maxIndex = endIndices.minOrNull() ?: elements.size

            val elements2 = elements.subList(minIndex, maxIndex)
            elements2.forEach { element ->
                element.select("strong").forEach { subElement ->
                    if (subElement.html() == "<u>Search for series of same genre(s)</u>") {
                        subElement.remove()
                    }
                }
            }

            description = elements2.joinToString("\n\n") { it.text() }

            // add alternative name to manga description
            document.selectFirst(altNameSelector)?.ownText()?.let {
                if (it.isBlank().not() && it.notUpdating()) {
                    description = when {
                        description.isNullOrBlank() -> "$altName $it"
                        else -> "$description\n\n$altName $it"
                    }
                }
            }
        }
    }
}
