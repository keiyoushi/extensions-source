package eu.kanade.tachiyomi.extension.en.readchainsawmanmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadChainsawManMangaOnline : MangaCatalog("Read Chainsaw Man Manga Online", "https://ww1.readchainsawman.com", "en") {
    override val sourceList = listOf(
        Pair("Chainsaw Man", "$baseUrl/manga/chainsaw-man/"),
        Pair("Fire Punch", "$baseUrl/manga/fire-punch/"),
        Pair("Nayuta", "$baseUrl/manga/yogen-no-nayuta/"),
        Pair("Colored", "$baseUrl/manga/chainsaw-man-colored/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
