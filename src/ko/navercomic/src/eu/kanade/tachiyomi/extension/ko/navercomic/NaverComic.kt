package eu.kanade.tachiyomi.extension.ko.navercomic

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NaverWebtoon : NaverComicBase("webtoon") {
    override val name = "Naver Webtoon"

    override fun popularMangaRequest(page: Int) = GET("$mobileUrl/$mType/weekday?sort=ALL_READER")
    override fun popularMangaSelector() = ".list_toon > [class='item ']"
    override fun popularMangaNextPageSelector() = null
    override fun popularMangaFromElement(element: Element): SManga {
        val thumb = element.select("img").attr("src")
        val title = element.select("strong").text()
        val author = element.select("span.author").text().trim().split(" / ").joinToString()
        val url = element.select("a").attr("href")

        val manga = SManga.create()
        manga.url = url
        manga.title = title
        manga.author = author
        manga.thumbnail_url = thumb
        return manga
    }

    override fun latestUpdatesRequest(page: Int) = GET("$mobileUrl/$mType/weekday?sort=UPDATE")
    override fun latestUpdatesSelector() = ".list_toon > [class='item ']"
    override fun latestUpdatesNextPageSelector() = null
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) =
        throw UnsupportedOperationException("Not used")
}

class NaverBestChallenge : NaverComicChallengeBase("bestChallenge") {
    override val name = "Naver Webtoon Best Challenge"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/$mType/list?order=VIEW&page=$page")
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/$mType/list?order=UPDATE&page=$page")

    override fun mangaDetailsParse(document: Document) =
        throw UnsupportedOperationException("Not used")
}

class NaverChallenge : NaverComicChallengeBase("challenge") {
    override val name = "Naver Webtoon Challenge"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/$mType/list?order=VIEW&page=$page")
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/$mType/list?order=UPDATE&page=$page")

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        return if (date.contains(":")) {
            Calendar.getInstance().timeInMillis
        } else {
            return try {
                SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).parse(date)?.time ?: 0L
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    override fun mangaDetailsParse(document: Document) =
        throw UnsupportedOperationException("Not used")
}
