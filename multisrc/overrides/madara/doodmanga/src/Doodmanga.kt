package eu.kanade.tachiyomi.extension.th.doodmanga

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Doodmanga : Madara("Doodmanga", "https://www.doodmanga.com", "th", SimpleDateFormat("dd MMMMM yyyy", Locale("th"))) {
    override val filterNonMangaItems = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(ScrambledImageInterceptor)
        .build()

    override val pageListParseSelector = "div.text-center > p > img, div.text-center > img, div.text-center > script"
    override fun pageListParse(document: Document): List<Page> {
        super.countViews(document)

        return document.select(pageListParseSelector).mapIndexedNotNull { index, element ->
            val src = when (element.tagName()) {
                "img" -> element.attr("src")
                "script" -> {
                    if (element.data().startsWith("eval(")) {
                        val quickJs = QuickJs.create()
                        val result = quickJs.evaluate(element.data().removePrefix("eval")) as String
                        quickJs.close()

                        val src = result.substringAfter("<img src='").substringBefore("'/>")
                        val sovleImage = result.substringAfter("var sovleImage=[[").substringBefore("]]").split("],[").map { values ->
                            values.replace("[", "").replace("]", "").split(",").map { it.removeSurrounding("\"") }
                        }

                        val segmentWidth = result.substringAfter("width:\"+").substringBefore("+\"px")
                        val segmentHeight = result.substringAfter("height: \"+").substringBefore("+\"px")

                        "$src?sovleImage=${sovleImage.joinToString("::") { (x, y, px, py) -> "$x,$y,$px,$py" }}&segmentWidth=$segmentWidth&segmentHeight=$segmentHeight"
                    } else {
                        null
                    }
                }
                else -> null
            }

            if (src == null) {
                null
            } else {
                Page(index, document.location(), src)
            }
        }
    }
}
