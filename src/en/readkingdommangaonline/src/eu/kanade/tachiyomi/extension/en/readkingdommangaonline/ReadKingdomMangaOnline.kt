package eu.kanade.tachiyomi.extension.en.readkingdommangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadKingdomMangaOnline : MangaCatalog("Read Kingdom Manga Online", "https://ww2.readkingdom.com", "en") {
    override val sourceList = listOf(
        Pair("Kingdom", "$baseUrl/manga/kingdom/"),
        Pair("Li Mu", "$baseUrl/manga/li-mu/"),
        Pair("Meng Wu & Chu Zi", "$baseUrl/manga/meng-wu-and-chu-zi-one-shot/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
