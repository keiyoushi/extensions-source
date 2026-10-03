package eu.kanade.tachiyomi.extension.en.readberserkmanga

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParseDate
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ReadBerserkManga : MangaCatalog() {
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

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.select("div.card-body > p").text()
        title = document.select("h2 > span").text()
        thumbnail_url = document.select(".card-img-right").attr("abs:src")
    }

    override fun chapterListSelector(): String = "tbody > tr"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("td:first-child").text()
        url = element.select("a.btn-primary").attr("abs:href")
        date_upload = dateFormat.tryParseDate(element.select("td:nth-child(2)").text())
    }

    override fun pageListParse(document: Document): List<Page> = document.select("div.pages img.pages__img").mapIndexed { index, element ->
        Page(index, imageUrl = element.attr("abs:src"))
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
