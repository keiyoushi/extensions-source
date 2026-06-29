package eu.kanade.tachiyomi.extension.ko.navercomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class NaverWebtoon : NaverComicBase("webtoon") {
    override val name = "Naver Webtoon"

    override fun popularMangaRequest(page: Int) = GET("$mobileUrl/$mType/weekday?sort=ALL_READER", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".list_toon > [class='item ']").map { element ->
            SManga.create().apply {
                url = element.selectFirst("a")?.attr("href") ?: ""
                title = element.selectFirst("strong")?.text() ?: ""
                author = element.selectFirst("span.author")?.text()?.split(" / ")?.joinToString() ?: ""
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$mobileUrl/$mType/weekday?sort=UPDATE", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)
}

class NaverBestChallenge : NaverComicChallengeBase("bestChallenge") {
    override val name = "Naver Webtoon Best Challenge"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/$mType/list?order=VIEW&page=$page", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/$mType/list?order=UPDATE&page=$page", headers)
}

class NaverChallenge : NaverComicChallengeBase("challenge") {
    override val name = "Naver Webtoon Challenge"

    override val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/$mType/list?order=VIEW&page=$page", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/$mType/list?order=UPDATE&page=$page", headers)
}
