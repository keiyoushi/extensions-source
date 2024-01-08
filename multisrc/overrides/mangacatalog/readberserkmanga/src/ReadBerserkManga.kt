package eu.kanade.tachiyomi.extension.en.readberserkmanga

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ReadBerserkManga : MangaCatalog("Read Berserk Manga", "https://readberserk.com", "en") {
    override val sourceList = listOf(
        Pair("Berserk", "$baseUrl/manga/berserk/"),
        Pair("Guidebook", "$baseUrl/manga/berserk-official-guidebook/"),
        Pair("Colored", "$baseUrl/manga/berserk-colored/"),
        Pair("Motion Comic", "$baseUrl/manga/berserk-the-motion-comic/"),
        Pair("Duranki", "$baseUrl/manga/duranki/"),
        Pair("Gigantomakhia", "$baseUrl/manga/gigantomakhia/"),
        Pair("Berserk Spoilers & RAW", "$baseUrl/manga/berserk-spoilers-raw/"),
    ).sortedBy { it.first }.distinctBy { it.second }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.select("div.card-body > p").text()
        title = document.select("h2 > span").text()
        thumbnail_url = document.select(".card-img-right").attr("src")
    }
    override fun chapterListSelector(): String = "tbody > tr"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("td:first-child").text()
        url = element.select("a.btn-primary").attr("abs:href")
        date_upload = System.currentTimeMillis() // I have no idear how to parse Date stuff
    }
}
