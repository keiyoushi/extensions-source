package eu.kanade.tachiyomi.extension.en.readnanatsunotaizai7deadlysinsmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadNanatsuNoTaizai7DeadlySinsMangaOnline : MangaCatalog("Read Nanatsu no Taizai 7 Deadly Sins Manga Online", "https://ww3.read7deadlysins.com", "en") {
    override val sourceList = listOf(
        Pair("Mokushiroku no Yonkishi", "$baseUrl/manga/four-horsemen-of-the-apocalypse/"),
        Pair("7DS: School", "$baseUrl/manga/mayoe-nanatsu-no-taizai-gakuen/"),
        Pair("7DS:7 Days", "$baseUrl/manga/nanatsu-no-taizai-seven-days/"),
        Pair("7DS:Vampires", "$baseUrl/manga/nanatsu-no-taizai-vampires-of-edinburgh/"),
        Pair("Queen of Altar", "$baseUrl/manga/the-queen-of-the-altar/"),
        Pair("7DS: 7 Colors", "$baseUrl/manga/nanatsu-no-taizai-nanairo-no-tsuioku/"),
        Pair("7DS x FT", "$baseUrl/manga/fairy-tail-x-nanatsu-no-taizai-christmas-special/"),
        Pair("7 Deadly Sins", "$baseUrl/manga/nanatsu-no-taizai/"),
        Pair("7DS:7 Scars", "$baseUrl/manga/nanatsu-no-taizai-the-seven-scars-which-they-left-behind/"),
        Pair("Kongou Banchou", "$baseUrl/manga/kongou-banchou/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
