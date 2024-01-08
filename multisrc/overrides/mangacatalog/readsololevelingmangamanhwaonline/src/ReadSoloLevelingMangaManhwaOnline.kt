package eu.kanade.tachiyomi.extension.en.readsololevelingmangamanhwaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadSoloLevelingMangaManhwaOnline : MangaCatalog("Read Solo Leveling Manga Manhwa Online", "https://readsololeveling.org", "en") {
    override val sourceList = listOf(
        Pair("Solo Leveling", "$baseUrl/manga/solo-leveling/"),
        Pair("Light Novel", "$baseUrl/manga/solo-leveling-novel/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
