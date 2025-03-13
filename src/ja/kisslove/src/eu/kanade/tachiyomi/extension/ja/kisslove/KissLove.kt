package eu.kanade.tachiyomi.extension.ja.kisslove

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element
import java.util.Calendar

class KissLove : FMReader("KissLove", "https://klz9.com", "ja") {

    override val altNameSelector = "li:contains(Other name (s))"

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/manga-list.html?page=$page&sort=last_update")

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = MID_URL_REGEX.find(manga.url)
            ?.groupValues?.get(1)
            ?: throw Exception("Could not find manga id")

        val xhrUrl = "$baseUrl/${generateRandomStr(25)}.lstc".toHttpUrl().newBuilder()
            .addQueryParameter("slug", mangaId)
            .build()

        return GET(xhrUrl, headers)
    }

    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter {
        return SChapter.create().apply {
            element.select(chapterUrlSelector).first()!!.let {
                setUrlWithoutDomain("$baseUrl/${it.attr("href")}")
                name = it.attr("title")
            }

            date_upload = element.select(chapterTimeSelector)
                .let { if (it.hasText()) parseChapterDate(it.text()) else 0 }
        }
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

    override fun pageListRequest(chapter: SChapter): Request {
        val request = super.pageListRequest(chapter)
        val response = client.newCall(request).execute()
        val document = response.asJsoup()

        val chapterId = document.selectFirst("#chapter")
            ?.`val`()
            ?: throw Exception("Could not find chapter id")

        val xhrUrl = "$baseUrl/${generateRandomStr(30)}.iog".toHttpUrl().newBuilder()
            .addQueryParameter("cid", chapterId)
            .build()

        return GET(xhrUrl, headers)
    }

    private fun generateRandomStr(length: Int): String {
        return (1..length).map { TO_PATH_CHARACTERS.random() }.joinToString("")
    }

    companion object {
        private val TO_PATH_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val MID_URL_REGEX = "-([^.]+).html".toRegex()
    }
}
