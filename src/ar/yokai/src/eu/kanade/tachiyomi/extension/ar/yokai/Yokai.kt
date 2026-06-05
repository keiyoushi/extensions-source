package eu.kanade.tachiyomi.extension.ar.yokai

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Yokai : ZeistManga("Yokai", "https://yokai-team.blogspot.com", "ar") {

    override val preferChapterUpdatedDate = true

    override val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Dubai")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            response.request.url.toString(),
        )

        val originalList = super.chapterListParse(response)
            .map { chapter ->
                val chapterNumberStr: String? = if (
                    chapter.name.startsWith("Chapter", ignoreCase = true)
                ) {
                    val numberPart = chapter.name.substringAfter("Chapter").trim().substringBefore(" ")
                    chapter.name = "الفصل $numberPart"
                    numberPart
                } else {
                    arabicChapterRegex.find(chapter.name)?.groupValues?.get(1)
                }

                chapterNumberStr?.toFloatOrNull()?.let { chapter.chapter_number = it }
                chapter
            }

        val additionalChapters = document.select("div#download > div.index-list > a")
            .map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    val text = element.text().trim()
                    name = text
                    chapter_number = text.substringBefore(' ').toFloatOrNull() ?: 1F
                }
            }

        return (originalList + additionalChapters)
            .distinctBy { it.url.substringBefore("?") }
    }

    companion object {
        private val arabicChapterRegex = Regex("""الفصل\s*(\d+(?:\.\d+)?)""")
    }
}
