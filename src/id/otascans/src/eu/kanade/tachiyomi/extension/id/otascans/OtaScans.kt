package eu.kanade.tachiyomi.extension.id.otascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class OtaScans :
    Madara(
        "Ota Scans",
        "https://yurilabs.my.id",
        "id",
        SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val mangaSubString = "series"

    override fun popularMangaSelector() = "div.manga__item"
    override fun latestUpdatesSelector() = "div.manga__item"
    override fun searchMangaSelector() = "div.manga__item"

    override val mangaDetailsSelectorTitle = "h1.post-title"

    override val useNewChapterEndpoint = false

    override fun chapterListRequest(manga: SManga): Request {
        val url = if (manga.url.endsWith("/")) manga.url else "${manga.url}/"
        return POST(baseUrl + url + "ajax/chapters/?t=1", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        document.select(chapterListSelector()).mapTo(chapters) { chapterFromElement(it) }

        val lastPage = document.select("div.pagination a[data-page]")
            .mapNotNull { it.attr("data-page").toIntOrNull() }
            .maxOrNull() ?: 1

        val requestUrl = response.request.url.toString().substringBefore("?")

        for (page in 2..lastPage) {
            val nextRequest = POST("$requestUrl?t=$page", headers)
            val nextResponse = client.newCall(nextRequest).execute()

            if (nextResponse.isSuccessful) {
                nextResponse.use {
                    it.asJsoup().select(chapterListSelector()).mapTo(chapters) { element ->
                        chapterFromElement(element)
                    }
                }
            } else {
                nextResponse.close()
            }
        }

        return chapters
    }

    private val dayMonthFormat = SimpleDateFormat("d MMMM", Locale.ENGLISH)

    override fun parseChapterDate(date: String?): Long {
        val parsed = super.parseChapterDate(date)
        if (parsed != 0L) return parsed

        val cleanDate = date?.trim() ?: return 0L
        return try {
            val parsedDate = dayMonthFormat.parse(cleanDate) ?: return 0L
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)

            calendar.time = parsedDate
            calendar.set(Calendar.YEAR, currentYear)

            if (calendar.timeInMillis > System.currentTimeMillis()) {
                calendar.add(Calendar.YEAR, -1)
            }

            calendar.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }
}
