package eu.kanade.tachiyomi.extension.en.readdragonballsuperchoumangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadDragonBallSuperChouMangaOnline : MangaCatalog("Read Dragon Ball Super Chou Manga Online", "https://ww6.dbsmanga.com", "en") {
    override val sourceList = listOf(
        Pair("Dragon Ball Super", "$baseUrl/manga/dragon-ball-super/"),
        Pair("Dragon Ball", "$baseUrl/manga/dragon-ball/"),
        Pair("Bardock", "$baseUrl/manga/dragon-ball-episode-of-bardock/"),
        Pair("Victory Mission", "$baseUrl/manga/dragon-ball-heroes-victory-mission/"),
        Pair("DB SD", "$baseUrl/manga/dragon-ball-sd/"),
        Pair("Yamcha Isekai", "$baseUrl/manga/dragon-ball-side-story-yamcha-isekai/"),
        Pair("DB x OP", "$baseUrl/manga/dragon-ball-x-one-piece/"),
        Pair("Rebirth of F", "$baseUrl/manga/dragon-ball-z-rebirth-of-f/"),
        Pair("Dark Realm", "$baseUrl/manga/super-dragon-ball-heroes-dark-demon-realm-mission/"),
        Pair("Universe Mission", "$baseUrl/manga/super-dragon-ball-heroes-universe-mission/"),
        Pair("Colored: Saiyan Arc", "$baseUrl/manga/dragon-ball-full-color-saiyan-arc/"),
        Pair("Colored: Freeza Arc", "$baseUrl/manga/dragon-ball-full-color-freeza-arc/"),
        Pair("Big Bang Mission!", "$baseUrl/manga/super-dragon-ball-heroes-big-bang-mission/"),
        Pair("DBS Colored", "$baseUrl/manga/dragon-ball-super-colored/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
