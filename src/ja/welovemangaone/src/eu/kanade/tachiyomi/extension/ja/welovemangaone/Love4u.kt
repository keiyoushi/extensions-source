package eu.kanade.tachiyomi.extension.ja.welovemangaone

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class Love4u : FMReader() {

    override fun latestUpdatesUrl(page: Int) = "$baseUrl/manga-list.html?page=$page&sort=last_update"

    override suspend fun fetchChapterList(manga: SManga, mangaPage: Document): List<SChapter> {
        val mangaId = MID_URL_REGEX.find(manga.url)
            ?.groupValues?.get(1)
            ?: throw Exception("Could not find manga id")

        val xhrUrl = "$baseUrl/app/manga/controllers/cont.Listchapter.php".toHttpUrl().newBuilder()
            .addQueryParameter("mid", mangaId)
            .build()

        return chapterListParse(client.get(xhrUrl).asJsoup())
    }

    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter = SChapter.create().apply {
        element.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            name = it.attr("title")
        }

        date_upload = element.select(chapterTimeSelector)
            .let { if (it.hasText()) parseChapterDate(it.text()) else 0 }
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[dateValueIndex].toInt()
        val chapterDate = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (date.split(' ')[dateWordIndex]) {
            "mins", "minutes" -> chapterDate.add(Calendar.MINUTE, -value)
            "hours" -> chapterDate.add(Calendar.HOUR_OF_DAY, -value)
            "days" -> chapterDate.add(Calendar.DATE, -value)
            "weeks" -> chapterDate.add(Calendar.DATE, -value * 7)
            "months" -> chapterDate.add(Calendar.MONTH, -value)
            "years" -> chapterDate.add(Calendar.YEAR, -value)
            else -> return 0
        }

        return chapterDate.timeInMillis
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        val chapterId = document.selectFirst("#chapter")
            ?.`val`()
            ?: throw Exception("Could not find chapter id")

        val xhrUrl = "$baseUrl/app/manga/controllers/cont.listImg.php".toHttpUrl().newBuilder()
            .addQueryParameter("cid", chapterId)
            .build()

        return pageListParse(client.get(xhrUrl).asJsoup())
    }

    companion object {
        private val MID_URL_REGEX = "(\\d+)/".toRegex()
    }
}
