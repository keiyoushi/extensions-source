package eu.kanade.tachiyomi.extension.en.readgoblinslayermangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ReadGoblinSlayerMangaOnline : MangaCatalog("Read Goblin Slayer Manga Online", "https://manga.watchgoblinslayer.com", "en") {
    override val sourceList = listOf(
        Pair("Goblin Slayer", "$baseUrl/manga/goblin-slayer/"),
        Pair("Side Story: Brand New Day", "$baseUrl/manga/goblin-slayer-side-story-brand-new-day/"),
        Pair("Side Story: Year One", "$baseUrl/manga/goblin-slayer-side-story-year-one/"),
        Pair("Side Story: Gaiden 2", "$baseUrl/manga/goblin-slayer-gaiden-2-tsubanari-no-daikatana/"),
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
