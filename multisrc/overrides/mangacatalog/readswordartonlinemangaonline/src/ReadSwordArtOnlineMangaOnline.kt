package eu.kanade.tachiyomi.extension.en.readswordartonlinemangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ReadSwordArtOnlineMangaOnline : MangaCatalog("Read Sword Art Online Manga Online", "https://manga.watchsao.tv", "en") {
    override val sourceList = listOf(
        Pair("SAO", "$baseUrl/manga/sword-art-online/"),
        Pair("Alicization", "$baseUrl/manga/sword-art-online-project-alicization/"),
        Pair("Progressive", "$baseUrl/manga/sword-art-online-progressive/"),
        Pair("Progressive 2", "$baseUrl/manga/sword-art-online-progressive-barcarolle-of-froth/"),
        Pair("Fairy Dance", "$baseUrl/manga/sword-art-online-fairy-dance/"),
        Pair("GGO", "$baseUrl/manga/sword-art-online-alternative-gun-gale-online/"),
        Pair("4-koma", "$baseUrl/manga/sword-art-online-4-koma/"),
        Pair("Aincrad", "$baseUrl/manga/sword-art-online-aincrad-night-of-kirito/"),
        Pair("Girls Ops", "$baseUrl/manga/sword-art-online-girls-ops/"),
        Pair("Anthology", "$baseUrl/manga/sword-art-online-comic-anthology/"),
        Pair("Lycoris", "$baseUrl/manga/sword-art-online-lycoris/"),
        Pair("Hollow Realization", "$baseUrl/manga/sword-art-online-hollow-realization/"),
        Pair("Ordinal Scale", "$baseUrl/manga/sword-art-online-ordinal-scale/"),
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
