package eu.kanade.tachiyomi.extension.tr.raindropfansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class RaindropFansub : MangaThemesia(
    "Raindrop Fansub",
    "https://www.raindropteamfan.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Tür) a"

    override fun chapterListParse(response: Response): List<SChapter> {
        // "İlk Bölüm" points to the first chapter, but is often wrong on the site
        // We look at "Son Bölüm" to find the last chapter and sort accordingly
        val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
        val chapters = super.chapterListParse(response)

        val lastChapterUrl = document
            .selectFirst("a:has(.epcurlast)")
            ?.attr("href")
            ?.let {
                val dummyChapter = SChapter.create()
                dummyChapter.setUrlWithoutDomain(it)
                dummyChapter.url
            }

        return when (lastChapterUrl) {
            chapters.first().url -> chapters
            chapters.last().url -> chapters.reversed()
            else -> chapters.reversed()
        }
    }
}
