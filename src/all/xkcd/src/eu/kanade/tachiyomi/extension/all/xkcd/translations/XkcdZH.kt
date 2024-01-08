package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
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

class XkcdZH : Xkcd("https://xkcd.tw", "zh", "yyyy-MM-dd HH:mm:ss") {
    override val archive = "/api/strips.json"

    override val creator = "兰德尔·门罗"

    override val synopsis = "這裡翻譯某個關於浪漫、諷刺、數學、以及語言的漫畫"

    // Google translated, sorry
    override val interactiveText =
        "要體驗本漫畫的互動版請在WebView/瀏覽器中打開。"

    override val altTextUrl = CJK_ALT_TEXT_URL

    override val imageSelector = "#content > img:not([id])"

    private val json by injectLazy<Json>()

    override fun String.numbered(number: Any) = "[$number] $this"

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl, headers)

    override fun chapterListParse(response: Response) =
        json.parseToJsonElement(response.body.string()).jsonObject.values.map {
            val obj = it.jsonObject
            val number = obj["id"]!!.jsonPrimitive.content
            val title = obj["title"]!!.jsonPrimitive.content
            val date = obj["translate_time"]!!.jsonPrimitive.content
            SChapter.create().apply {
                url = "/$number"
                name = title.numbered(number)
                chapter_number = number.toFloat()
                date_upload = date.timestamp()
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        // if img tag is empty then it is an interactive comic
        val img = response.asJsoup().selectFirst(imageSelector) ?: error(interactiveText)

        val image = img.attr("abs:src")

        // create a text image for the alt text
        val text = img.attr("alt") + "\n\n" + img.attr("title")

        return listOf(Page(0, "", image), Page(1, "", text.image()))
    }

    override val chapterListSelector: String
        get() = throw UnsupportedOperationException("Not used")
}
