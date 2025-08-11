package eu.kanade.tachiyomi.extension.en.readhunterxhuntermangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadHunterxHunterMangaOnline : MangaCatalog("Read Hunter x Hunter Manga Online", "https://ww6.readhxh.com", "en") {
    override val sourceList = listOf(
        Pair("Hunter x Hunter", "$baseUrl/manga/hunter-x-hunter/"),
        Pair("HxH Colored", "$baseUrl/manga/hunter-x-hunter-colored/"),
        Pair("Yu Yu Hakusho", "$baseUrl/manga/yu-yu-hakusho/"),
        Pair("Level E", "$baseUrl/manga/level-e/"),
        Pair("Kurapika Spinoff", "$baseUrl/manga/hunter-x-hunter-kurapika-tsuioku-hen/"),
    )
}
