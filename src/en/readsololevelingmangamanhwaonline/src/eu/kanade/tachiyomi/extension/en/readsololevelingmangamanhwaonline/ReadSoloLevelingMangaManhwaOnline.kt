package eu.kanade.tachiyomi.extension.en.readsololevelingmangamanhwaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadSoloLevelingMangaManhwaOnline : MangaCatalog() {
    override val sourceList = listOf(
        Pair("Solo Leveling Manhwa", "$baseUrl/manga/solo-leveling/"),
        Pair("Solo Leveling Light Novel", "$baseUrl/manga/solo-leveling-light-novel/"),
        // Pair("Audio book", "$baseUrl/manga/solo-leveling-audiobook/"), // Audio
        Pair("Solo Leveling : Ragnarok", "$baseUrl/manga/solo-leveling-ragnarok/"),
        Pair("SL: Ragnarok Novel", "$baseUrl/manga/solo-leveling-ragnarok-novel/"),
    )
}
