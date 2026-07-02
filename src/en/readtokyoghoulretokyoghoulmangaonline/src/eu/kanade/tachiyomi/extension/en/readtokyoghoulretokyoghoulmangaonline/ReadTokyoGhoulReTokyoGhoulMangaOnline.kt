package eu.kanade.tachiyomi.extension.en.readtokyoghoulretokyoghoulmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadTokyoGhoulReTokyoGhoulMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        Pair("Tokyo Ghoul", "$baseUrl/manga/tokyo-ghoul/"),
        Pair("Tokyo Ghoul Jack", "$baseUrl/manga/tokyo-ghoul-jack/"),
        Pair("Tokyo Ghoul: re Colored", "$baseUrl/manga/tokyo-ghoulre-colored/"),
        Pair("Gorilla", "$baseUrl/manga/this-gorilla-will-die-in-1-day/"),
        Pair("Zakki", "$baseUrl/manga/tokyo-ghoul-zakki/"),
        Pair("Light Novel", "$baseUrl/manga/tokyo-ghoul-re-light-novels/"),
        Pair("Choujin X", "$baseUrl/manga/choujin-x/"),
        Pair("Tokyo Ghoul re", "$baseUrl/manga/tokyo-ghoulre/"),
    )
}
