package eu.kanade.tachiyomi.extension.en.readkagurabachimangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadKagurabachiMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        Pair("Kagurabachi", "$baseUrl/manga/kagurabachi/"),
        Pair("Enten", "$baseUrl/manga/enten/"),
        Pair("Chain", "$baseUrl/manga/chain/"),
        Pair("Roku no Meiyaku", "$baseUrl/manga/roks-death-testament/"),
        Pair("Madogiwa de Amu", "$baseUrl/manga/knitting-by-the-window/"),
        Pair("Farewell! Cherry Boy!", "$baseUrl/manga/farewell-cherry-boy/"),
    )
}
