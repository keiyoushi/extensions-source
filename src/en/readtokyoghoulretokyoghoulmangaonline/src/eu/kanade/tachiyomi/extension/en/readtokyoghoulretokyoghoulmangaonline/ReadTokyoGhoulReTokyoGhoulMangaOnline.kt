package eu.kanade.tachiyomi.extension.en.readtokyoghoulretokyoghoulmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadTokyoGhoulReTokyoGhoulMangaOnline : MangaCatalog("Read Tokyo Ghoul Re & Tokyo Ghoul Manga Online", "https://ww8.tokyoghoulre.com", "en") {
    override val sourceList = listOf(
        Pair("Tokyo Ghoul", "$baseUrl/manga/tokyo-ghoul/"),
        Pair("Tokyo Ghoul:re", "$baseUrl/manga/tokyo-ghoulre/"),
        Pair("TG Jack", "$baseUrl/manga/tokyo-ghoul-jack/"),
        Pair("TGre Colored", "$baseUrl/manga/tokyo-ghoulre-colored/"),
        Pair("Gorilla", "$baseUrl/manga/this-gorilla-will-die-in-1-day/"),
        Pair("ArtBook", "$baseUrl/manga/tokyo-ghoul-zakki/"),
        Pair("TG Light Novel", "$baseUrl/manga/tokyo-ghoul-re-light-novels/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
