package eu.kanade.tachiyomi.extension.zh.bakamh

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Bakamh : Madara(
    "巴卡漫画",
    "https://bakamh.com",
    "zh",
    SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINESE),
) {
    override val mangaDetailsSelectorStatus = ".post-content_item:contains(状态) .summary-content"

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder()
            .add("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    val chapterSelectors = listOf(
        ".chapter-loveYou",
        "li a[onclick]",
        "li a",
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url.toString().lowercase()
        val doc = response.asJsoup()
        for (selector in chapterSelectors) {
            val chapters = doc.select(selector)
                .mapNotNull { paresChapter(it, mangaUrl) }
            if (chapters.isNotEmpty()) {
                return chapters
            }
        }
        return emptyList()
    }

    fun paresChapter(element: Element, mangaUrl: String): SChapter? {
        if (element.hasAttr("storage-chapter-url")) {
            return SChapter.create().apply {
                url = element.absUrl("storage-chapter-url")
                name = element.text()
                chapter_number = 0F
            }
        }

        return element.attributes()
            .find { attr ->
                val value = attr.value.lowercase()
                value.startsWith(mangaUrl) && value != mangaUrl
            }
            ?.let { attr ->
                SChapter.create().apply {
                    url = element.absUrl(attr.key)
                    name = element.text()
                    chapter_number = 0F
                }
            }
    }
}
