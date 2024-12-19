package eu.kanade.tachiyomi.extension.en.readchainsawmanmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadChainsawManMangaOnline : MangaCatalog("Read Chainsaw Man Manga Online", "https://ww4.readchainsawman.com", "en") {
    override val sourceList = listOf(
        // Chainsaw Man
        Pair("Chainsaw Man", "$baseUrl/manga/chainsaw-man/"),
        Pair("Chainsaw Man Colored", "$baseUrl/manga/chainsaw-man-colored/"),
        Pair("Chainsaw Man: Buddy Stories", "$baseUrl/manga/chainsaw-man-buddy-stories/"),

        // Other titles
        Pair("Fire Punch", "$baseUrl/manga/fire-punch/"),
        Pair("Yogen no Nayuta", "$baseUrl/manga/yogen-no-nayuta/"),
        Pair("Look Back", "$baseUrl/manga/look-back/"),
        Pair("Just Listen to the Song", "$baseUrl/manga/futsuu-ni-kiite-kure/"),
        Pair("Sayonara Eri (Goodbye, Eri)", "$baseUrl/manga/sayonara-eri-goodbye-eri/"),

        // Storybooks
        Pair("Tatsuki Fujimoto Before Chainsaw Man: 17-21", "$baseUrl/manga/17-21-fujimoto-tatsuki-tanpenshuu/"),
        Pair("Tatsuki Fujimoto Before Chainsaw Man: 22-26", "$baseUrl/manga/22-26-fujimoto-tatsuki-tanpenshuu/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
