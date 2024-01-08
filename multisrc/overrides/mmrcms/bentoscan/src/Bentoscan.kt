package eu.kanade.tachiyomi.extension.fr.bentoscan

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Bentoscan : MMRCMS("Bentoscan", "https://bentoscan.com", "fr") {
    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", IMG_URL)
            .set("Accept", "image/avif,image/webp,*/*")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun nullableChapterFromElement(element: Element): SChapter? {
        val chapter = SChapter.create()

        val titleWrapper = element.select("[class^=chapter-title-rtl]").first()!!
        val chapterElement = titleWrapper.getElementsByTag("a")!!
        val url = chapterElement.attr("href")

        chapter.url = getUrlWithoutBaseUrl(url)

        // Construct chapter names
        // Before -> Scan <manga_name> <chapter_number> VF: <chapter_number>
        // Now    -> Chapitre <chapter_number> : <chapter_title> OR Chapitre <chapter_number>
        val chapterText = chapterElement.text()
        val numberRegex = Regex("""[1-9]\d*(\.\d+)*""")
        val chapterNumber = numberRegex.find(chapterText)?.value.orEmpty()
        val chapterTitle = titleWrapper.getElementsByTag("em")!!.text()
        if (chapterTitle.toIntOrNull() != null) {
            chapter.name = "Chapitre $chapterNumber"
        } else {
            chapter.name = "Chapitre $chapterNumber : $chapterTitle"
        }

        // Parse date
        val dateText = element.getElementsByClass("date-chapter-title-rtl").text().trim()

        chapter.date_upload = runCatching {
            dateFormat.parse(dateText)?.time
        }.getOrNull() ?: 0L

        return chapter
    }

    companion object {
        private const val IMG_URL = "https://scansmangas.me"
        val dateFormat by lazy {
            SimpleDateFormat("d MMM. yyyy", Locale.US)
        }
    }
}
