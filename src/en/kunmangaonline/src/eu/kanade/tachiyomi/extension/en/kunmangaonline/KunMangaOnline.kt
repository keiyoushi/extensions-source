package eu.kanade.tachiyomi.extension.en.kunmangaonline

import eu.kanade.tachiyomi.multisrc.zinmangaart.UrlLayout
import eu.kanade.tachiyomi.multisrc.zinmangaart.ZinMangaArt
import keiyoushi.annotation.Source

@Source
abstract class KunMangaOnline : ZinMangaArt() {
    override val urlLayout = UrlLayout.LEGACY_MANGA_PATH_CHAPTER_PATH
}
