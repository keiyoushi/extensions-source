package eu.kanade.tachiyomi.extension.en.readkagurabachimangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadKagurabachiMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        "Kagurabachi" to "$baseUrl/manga/kagurabachi/",
        "Enten" to "$baseUrl/manga/enten/",
        "Chain" to "$baseUrl/manga/chain/",
        "Roku no Meiyaku" to "$baseUrl/manga/roks-death-testament/",
        "Madogiwa de Amu" to "$baseUrl/manga/knitting-by-the-window/",
        "Farewell! Cherry Boy!" to "$baseUrl/manga/farewell-cherry-boy/",
    )
}
