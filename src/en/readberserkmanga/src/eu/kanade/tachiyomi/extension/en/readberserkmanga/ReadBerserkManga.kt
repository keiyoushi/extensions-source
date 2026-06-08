package eu.kanade.tachiyomi.extension.en.readberserkmanga

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ReadBerserkManga : MangaCatalog("Read Berserk Manga", "https://readberserk.com", "en") {
    override val sourceList = listOf(
        Pair("Berserk", "$baseUrl/manga/berserk/"),
        Pair("Guidebook", "$baseUrl/manga/berserk-official-guidebook/"),
        Pair("Colored", "$baseUrl/manga/berserk-colored/"),
        // Pair("Motion Comic", "$baseUrl/manga/berserk-the-motion-comic/"), // Video
        Pair("Duranki", "$baseUrl/manga/duranki/"),
        Pair("Gigantomakhia", "$baseUrl/manga/gigantomakhia/"),
        Pair("Futatabi", "$baseUrl/manga/futatabi/"),
        Pair("Berserk Spoilers & RAW", "$baseUrl/manga/berserk-spoilers-raw/"),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            description = document.select("div.card-body > p").text()
            title = document.select("h2 > span").text()
            thumbnail_url = document.select(".card-img-right").attr("abs:src")
        }
    }

    override fun chapterListSelector(): String = "tbody > tr"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("td:first-child").text()
        url = element.select("a.btn-primary").attr("abs:href")
        date_upload = dateFormat.tryParse(element.select("td:nth-child(2)").text())
    }
}

private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
