package eu.kanade.tachiyomi.extension.en.readblackclovermangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadBlackCloverMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        Pair("Black Clover", "$baseUrl/manga/black-clover/"),
        Pair("Fan Colored", "$baseUrl/manga/black-clover-colored/"),
        Pair("Hungry Joker", "$baseUrl/manga/hungry-joker/"),
        Pair("Gaiden", "$baseUrl/manga/black-clover-gaiden-quartet-knights/"),
    )
}
