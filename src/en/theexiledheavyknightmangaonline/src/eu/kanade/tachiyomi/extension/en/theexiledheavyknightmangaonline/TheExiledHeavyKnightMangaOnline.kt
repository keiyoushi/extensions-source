package eu.kanade.tachiyomi.extension.en.theexiledheavyknightmangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import keiyoushi.annotation.Source

@Source
abstract class TheExiledHeavyKnightMangaOnline : SoloManga() {

    override val mangaTitle = "The Exiled Heavy Knight"

    override val chapterSlug = "the-exiled-heavy-knight-knows-how-to-game-the-system-chapter-"

    override val chapterListSelector = "ul.scroll-sm"

    override val pageSelector = ".post-single-content img, .entry-content img"

    override val imageHosts = listOf(
        "pic.readkakegurui.com",
        "img.theexiledheavyknight.com",
        "theexiledheavyknight.com/wp-content/uploads",
    )
}
