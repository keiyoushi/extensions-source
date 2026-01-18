package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class XkcdZH : Xkcd("https://xkcd.tw", "zh") {
    override val archive = "/api/strips.json"

    override val creator = "兰德尔·门罗"

    override val synopsis = "這裡翻譯某個關於浪漫、諷刺、數學、以及語言的漫畫"

    // Google translated, sorry
    override val interactiveText =
        "要體驗本漫畫的互動版請在WebView/瀏覽器中打開。"

    override val imageSelector = "#content > img:not([id])"

    private val json by injectLazy<Json>()

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val englishDates = getComicDateMappingFromEnglishArchive()

        return json.parseToJsonElement(response.body.string()).jsonObject.values.map {
            val obj = it.jsonObject
            val comicNumber = obj["id"]!!.jsonPrimitive.content.toInt()
            val title = obj["title"]!!.jsonPrimitive.content
            SChapter.create().apply {
                url = "/$comicNumber"
                name = chapterTitleFormatter(comicNumber, title)
                chapter_number = comicNumber.toFloat()

                // use English publication date instead of translation date
                date_upload = if (englishDates.containsKey(comicNumber)) {
                    englishDates[comicNumber]!!.timestamp()
                } else {
                    0L
                }
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        // if img tag is empty then it is an interactive comic
        val img = response.asJsoup().selectFirst(imageSelector) ?: error(interactiveText)

        val image = img.attr("abs:src")

        // create a text image for the alt text
        val text = TextInterceptorHelper.createUrl(img.attr("alt"), img.attr("title"))

        return listOf(Page(0, "", image), Page(1, "", text))
    }

    override val chapterListSelector: String
        get() = throw UnsupportedOperationException()
}
