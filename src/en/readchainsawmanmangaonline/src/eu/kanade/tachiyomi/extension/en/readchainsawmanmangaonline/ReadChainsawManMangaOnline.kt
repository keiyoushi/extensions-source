package eu.kanade.tachiyomi.extension.en.readchainsawmanmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadChainsawManMangaOnline : MangaCatalog("Read Chainsaw Man Manga Online", "https://ww5.readchainsawman.com", "en") {
    override val sourceList = listOf(
        Pair("Chainsaw Man", "$baseUrl/manga/chainsaw-man/"),
        Pair("17-21", "$baseUrl/manga/17-21-fujimoto-tatsuki-tanpenshuu/"),
        Pair("Fire Punch", "$baseUrl/manga/fire-punch/"),
        Pair("Nayuta", "$baseUrl/manga/yogen-no-nayuta/"),
        Pair("Look Back", "$baseUrl/manga/look-back/"),
        Pair("Light Novel", "$baseUrl/manga/chainsaw-man-buddy-stories/"),
        Pair("Colored", "$baseUrl/manga/chainsaw-man-colored/"),
        Pair("Listen to Song", "$baseUrl/manga/futsuu-ni-kiite-kure/"),
        Pair("Goodbye, Eri", "$baseUrl/manga/sayonara-eri-goodbye-eri/"),
        Pair("22-26", "$baseUrl/manga/22-26-fujimoto-tatsuki-tanpenshuu/"),
        Pair("Chainsaw Man Colored", "$baseUrl/manga/chainsaw-man-colored/"),
        Pair("Chainsaw Man: Buddy Stories", "$baseUrl/manga/chainsaw-man-buddy-stories/"),
    )
}
