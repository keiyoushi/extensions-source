package eu.kanade.tachiyomi.extension.en.zinmanganet

import eu.kanade.tachiyomi.multisrc.zinmangaart.UrlLayout
import eu.kanade.tachiyomi.multisrc.zinmangaart.ZinMangaArt
import keiyoushi.annotation.Source

@Source
abstract class ZinmangaNet : ZinMangaArt() {
    override val urlLayout = UrlLayout.LEGACY_MANGA_PATH_CHAPTER_SLUG
}
