package eu.kanade.tachiyomi.extension.en.readblackclovermangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadBlackCloverMangaOnline : MangaCatalog("Read Black Clover Manga Online", "https://ww10.readblackclover.com", "en") {
    override val sourceList = listOf(
        Pair("Black Clover", "$baseUrl/manga/black-clover/"),
        Pair("Fan Colored", "$baseUrl/manga/black-clover-colored/"),
        Pair("Hungry Joker", "$baseUrl/manga/hungry-joker/"),
        Pair("Gaiden", "$baseUrl/manga/black-clover-gaiden-quartet-knights/"),
    )
}
