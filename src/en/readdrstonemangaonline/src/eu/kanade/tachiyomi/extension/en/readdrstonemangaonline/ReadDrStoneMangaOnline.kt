package eu.kanade.tachiyomi.extension.en.readdrstonemangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadDrStoneMangaOnline : MangaCatalog("Read Dr. Stone Manga Online", "https://ww7.readdrstone.com", "en") {
    override val sourceList = listOf(
        Pair("Dr. Stone", "$baseUrl/manga/dr-stone/"),
        Pair("Dr Stone: Reboot", "$baseUrl/manga/dr-stone-reboot-byakuya/"),
        Pair("Sun-ken Rock", "$baseUrl/manga/sun-ken-rock/"),
        Pair("One Shots", "$baseUrl/manga/oneshot-by-boichi/"),
        Pair("Hotel", "$baseUrl/manga/hotel/"),
        Pair("Origin", "$baseUrl/manga/origin/"),
        Pair("Raqiya", "$baseUrl/manga/raqiya/"),
        Pair("Isekai Kenbunroku", "$baseUrl/manga/super-string-isekai-kenbunroku/"),
        Pair("Feed Yumin", "$baseUrl/manga/i-want-to-feed-yumin/"),
        Pair("Space Chef", "$baseUrl/manga/space-chef-caisar/"),
        Pair("Wallman", "$baseUrl/manga/wallman/"),
        Pair("Trillion Game", "$baseUrl/manga/trillion-game/"),
        Pair("The Marshal King", "$baseUrl/manga/the-marshal-king/"),
    )
}
