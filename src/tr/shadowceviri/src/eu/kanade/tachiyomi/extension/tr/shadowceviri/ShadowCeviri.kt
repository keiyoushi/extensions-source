package eu.kanade.tachiyomi.extension.tr.shadowceviri

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class ShadowCeviri : ZeistManga() {

    // Popular
    override val popularMangaSelector = ".PopularPosts article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = ".item-thumbnail > a"

    // Details
    override val mangaDetailsSelector = "#main"

    // Chapters
    override val chapterCategory = "Chapter"

    // Pages
    override val pageListSelector = "div.separator"
}
