package eu.kanade.tachiyomi.extension.en.readjujutsukaisenmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadJujutsuKaisenMangaOnline : MangaCatalog("Read Jujutsu Kaisen Manga Online", "https://ww5.readjujutsukaisen.com", "en") {
    override val sourceList = listOf(
        Pair("Jujutsu Kaisen", "$baseUrl/manga/jujutsu-kaisen/"),
        Pair("Jujutsu Kaisen 0", "$baseUrl/manga/jujutsu-kaisen-0/"),
        Pair("JJK Colored", "$baseUrl/manga/jujutsu-kaisen-colored/"),
        Pair("Fan Scan", "$baseUrl/manga/jujutsu-kaisen-fan-scan/"),
        Pair("JJK Light Novel", "$baseUrl/manga/jujutsu-kaisen-first-light-novel/"),
        Pair("2nd Light Novel", "$baseUrl/manga/jujutsu-kaisen-second-light-novel/"),
        Pair("No.9", "$baseUrl/manga/no-9/"),
        Pair("Fanbook", "$baseUrl/manga/jujutsu-kaisen-official-fanbook/"),
    )
}
