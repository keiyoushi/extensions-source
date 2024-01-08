package eu.kanade.tachiyomi.extension.zh.dongmanmanhua

import eu.kanade.tachiyomi.multisrc.webtoons.Webtoons
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class DongmanManhua : Webtoons("Dongman Manhua", "https://www.dongmanmanhua.cn", "zh", "", dateFormat = SimpleDateFormat("yyyy-M-d", Locale.ENGLISH)) {

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .removeAll("Referer")
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/dailySchedule", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/dailySchedule?sortOrder=UPDATE&webtoonCompleteType=ONGOING", headers)

    override fun parseDetailsThumbnail(document: Document): String? {
        return document.select("div.detail_body").attr("style").substringAfter("(").substringBefore(")")
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListSelector() = "ul#_listUl li"

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        var continueParsing = true
        val chapters = mutableListOf<SChapter>()

        while (continueParsing) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            document.select("div.paginate a[onclick] + a").let { element ->
                if (element.isNotEmpty()) {
                    document = client.newCall(GET(element.attr("abs:href"), headers)).execute().asJsoup()
                } else {
                    continueParsing = false
                }
            }
        }
        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.select("span.subj span").text()
            url = element.select("a").attr("href").substringAfter(".cn")
            date_upload = chapterParseDate(element.select("span.date").text())
        }
    }

    override fun getFilterList(): FilterList = FilterList()
}
