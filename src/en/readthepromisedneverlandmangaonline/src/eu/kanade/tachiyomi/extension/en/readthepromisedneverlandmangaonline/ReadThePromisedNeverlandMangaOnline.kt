package eu.kanade.tachiyomi.extension.en.readthepromisedneverlandmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadThePromisedNeverlandMangaOnline : MangaCatalog("Read The Promised Neverland Manga Online", "https://ww3.readneverland.com", "en") {
    override val sourceList = listOf(
        Pair("The Promised Neverland", "$baseUrl/manga/the-promised-neverland/"),
        Pair("Parody", "$baseUrl/manga/the-parodied-jokeland/"),
        Pair("Novels", "$baseUrl/manga/novels/"),
        Pair("Poppy no Negai", "$baseUrl/manga/poppy-no-negai/"),
        Pair("Author's One shot", "$baseUrl/manga/shinrei-shashinshi-kouno-saburou/"),
        Pair("Ashley Goeth", "$baseUrl/manga/ashley-goeth-no-yukue/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
