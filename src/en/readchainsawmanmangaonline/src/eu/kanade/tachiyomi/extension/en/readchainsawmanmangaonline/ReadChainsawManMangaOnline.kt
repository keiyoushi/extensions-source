package eu.kanade.tachiyomi.extension.en.readchainsawmanmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadChainsawManMangaOnline : MangaCatalog("Read Chainsaw Man Manga Online", "https://ww1.readchainsawman.com", "en") {
    override val sourceList = listOf(
        // Chainsaw Man
        Pair("Chainsaw Man", "$baseUrl/manga/chainsaw-man/"),
        Pair("Chainsaw Man: Colored", "$baseUrl/manga/chainsaw-man-colored/"),
        Pair("Chainsaw Man: Buddy Stories light novel", "$baseUrl/manga/chainsaw-man-buddy-stories/"),

        // Other titles
        Pair("Fire Punch", "$baseUrl/manga/fire-punch/"),
        Pair("Nayuta", "$baseUrl/manga/yogen-no-nayuta/"),
        Pair("Look Back", "$baseUrl/manga/look-back/"),
        Pair("Listen to Song", "$baseUrl/manga/futsuu-ni-kiite-kure/"),
        Pair("Goodbye, Eri", "$baseUrl/manga/sayonara-eri-goodbye-eri/"),

        // Storybooks
        Pair("17-21", "$baseUrl/manga/17-21-fujimoto-tatsuki-tanpenshuu/"),
        Pair("22-26", "$baseUrl/manga/22-26-fujimoto-tatsuki-tanpenshuu/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
