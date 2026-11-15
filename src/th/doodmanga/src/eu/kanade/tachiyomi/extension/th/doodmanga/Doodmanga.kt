package eu.kanade.tachiyomi.extension.th.doodmanga

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Doodmanga : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("th"))

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ScrambledImageInterceptor)

    override val pageListParseSelector = "div.text-center > p > img, div.text-center > img, div.text-center > script"

    override fun parsePages(document: Document) = document.select(pageListParseSelector).mapIndexedNotNull { index, element ->
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
            Page(index, imageUrl = src)
        }
    }

    override fun imageFromElement(element: Element) = element.attr("abs:data-srcset").takeIf(String::isNotBlank)?.getSrcSetImage()
        ?: super.imageFromElement(element)
}
