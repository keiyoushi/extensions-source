package eu.kanade.tachiyomi.extension.en.readdrstonemangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadDrStoneMangaOnline : MangaCatalog("Read Dr. Stone Manga Online", "https://ww3.readdrstone.com", "en") {
    override val sourceList = listOf(
        Pair("Dr. Stone", "$baseUrl/manga/dr-stone/"),
        Pair("Dr. Stone: Reboot", "$baseUrl/manga/dr-stone-reboot-byakuya/"),
        Pair("Sun-ken Rock", "$baseUrl/manga/sun-ken-rock/"),
        Pair("Origin", "$baseUrl/manga/origin/"),
        Pair("Raqiya", "$baseUrl/manga/raqiya/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
