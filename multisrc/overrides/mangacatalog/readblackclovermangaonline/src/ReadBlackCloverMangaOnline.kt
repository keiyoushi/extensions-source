package eu.kanade.tachiyomi.extension.en.readblackclovermangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadBlackCloverMangaOnline : MangaCatalog("Read Black Clover Manga Online", "https://ww7.readblackclover.com", "en") {
    override val sourceList = listOf(
        Pair("Black Clover", "$baseUrl/manga/black-clover"),
        Pair("Black Clover Gainden Quartet Knights", "$baseUrl/manga/black-clover-gaiden-quartet-knights"),
        Pair("Fan Colored", "$baseUrl/manga/black-clover-colored"),
        Pair("Hungry Joker", "$baseUrl/manga/hungry-joker"),
    )
}
