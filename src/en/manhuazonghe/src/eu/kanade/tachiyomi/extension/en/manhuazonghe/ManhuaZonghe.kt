package eu.kanade.tachiyomi.extension.en.manhuazonghe

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhuaZonghe : Madara() {
    override val useNewChapterEndpoint = false
    override val filterNonMangaItems = false
    override val mangaSubString = "manhua"
}
