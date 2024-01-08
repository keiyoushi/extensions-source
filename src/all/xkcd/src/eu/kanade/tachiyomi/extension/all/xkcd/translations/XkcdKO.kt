package eu.kanade.tachiyomi.extension.all.xkcd.translations

import eu.kanade.tachiyomi.extension.all.xkcd.Xkcd
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class XkcdKO : Xkcd("https://xkcdko.com", "ko") {
    override val creator = "랜들 먼로"

    override val synopsis = "사랑, 풍자, 수학, 그리고 언어에 관한 웹 만화."

    // Google translated, sorry
    override val interactiveText =
        "이 만화의 대화형 버전을 경험하려면 WebView/브라우저에서 엽니다."

    override val altTextUrl = CJK_ALT_TEXT_URL

    override val chapterListSelector = "#comicList > ol > li > a"

    override fun chapterListParse(response: Response) =
        response.asJsoup().select(chapterListSelector).map {
            SChapter.create().apply {
                url = it.attr("href")
                val number = it.attr("title")
                name = it.text().numbered(number)
                chapter_number = number.toFloat()
                // no dates available
                date_upload = 0L
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        // if the img tag is empty then it is an interactive comic
        val img = response.asJsoup().selectFirst(imageSelector) ?: error(interactiveText)

        // if an HD image is available it'll be the srcset attribute
        val image = when {
            !img.hasAttr("srcset") -> img.attr("abs:src")
            else -> img.attr("abs:srcset").substringBefore(' ')
        }

        // create a text image for the alt text
        val text = img.attr("alt") + "\n\n" + img.attr("title")

        return listOf(Page(0, "", image), Page(1, "", text.image()))
    }
}
