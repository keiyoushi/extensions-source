package eu.kanade.tachiyomi.extension.en.ruridragonmangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import keiyoushi.annotation.Source

@Source
abstract class RuriDragonMangaOnline : SoloManga() {

    override val mangaTitle = "RuriDragon"

    override val chapterSlug = "ruridragon-chapter-"

    override val imageHosts = listOf("img.ruridragon.com", "ruridragon.com/wp-content/uploads")
}
