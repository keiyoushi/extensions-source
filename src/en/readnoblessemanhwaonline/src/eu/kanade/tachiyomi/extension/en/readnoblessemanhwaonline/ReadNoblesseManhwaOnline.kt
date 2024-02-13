package eu.kanade.tachiyomi.extension.en.readnoblessemanhwaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ReadNoblesseManhwaOnline : MangaCatalog("Read Noblesse Manhwa Online", "https://ww2.readnoblesse.com", "en") {
    override val sourceList = listOf(
        Pair("Noblesse", "$baseUrl/manga/noblesse/"),
        Pair("Rai’s Adventure", "$baseUrl/manga/noblesse-rais-adventure/"),
        Pair("NOBLESSE S", "$baseUrl/manga/noblesse-s/"),
        Pair("Ability", "$baseUrl/manga/ability/"),
    ).sortedBy { it.first }.distinctBy { it.second }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.select("div.flex > div.py-2 > div:not(:first-child)").text()
        title = document.select(".container h1").text()
        thumbnail_url = document.select("img.rounded-full").attr("src")
    }

    override fun chapterListSelector(): String = "div.w-full > div > div.flex"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val name1 = element.select(".flex-col > a:not(.text-xs)").text()
        val name2 = element.select(".text-xs:not(a)").text()
        if (name2 == "") {
            name = name1
        } else {
            name = "$name1 - $name2"
        }
        url = element.select(".flex-col > a:not(.text-xs)").attr("abs:href")
        date_upload = System.currentTimeMillis()
    }
}
