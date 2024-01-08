package eu.kanade.tachiyomi.extension.en.readattackontitanshingekinokyojinmanga

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element

class ReadAttackOnTitanShingekiNoKyojinManga : MangaCatalog("Read Attack on Titan Shingeki no Kyojin Manga", "https://ww8.readsnk.com", "en") {
    override val sourceList = listOf(
        Pair("Shingeki No Kyojin", "$baseUrl/manga/shingeki-no-kyojin/"),
        Pair("Colored", "$baseUrl/manga/shingeki-no-kyojin-colored/"),
        Pair("Before the Fall", "$baseUrl/manga/shingeki-no-kyojin-before-the-fall/"),
        Pair("Lost Girls", "$baseUrl/manga/shingeki-no-kyojin-lost-girls/"),
        Pair("No Regrets", "$baseUrl/manga/attack-on-titan-no-regrets/"),
        Pair("Junior High", "$baseUrl/manga/attack-on-titan-junior-high/"),
        Pair("Harsh Mistress", "$baseUrl/manga/attack-on-titan-harsh-mistress-of-the-city/"),
        Pair("Anthology", "$baseUrl/manga/attack-on-titan-anthology/"),
        Pair("Art Book", "$baseUrl/manga/attack-on-titan-exclusive-art-book/"),
        Pair("Spoof", "$baseUrl/manga/spoof-on-titan/"),
        Pair("Guidebook", "$baseUrl/manga/attack-on-titan-guidebook-inside-outside/"),
        Pair("No Regrets Colored", "$baseUrl/manga/attack-on-titan-no-regrets-colored/"),
    ).sortedBy { it.first }.distinctBy { it.second }

    override fun chapterListSelector(): String = "div.w-full div.grid div.col-span-3"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst("a")!!
        name = listOfNotNull(
            urlElement.text(),
            element.selectFirst("div.text-xs")!!.text().takeUnless { it.isBlank() },
        ).joinToString(" - ") { it.trim() }
        url = urlElement.attr("abs:href")
    }
}
