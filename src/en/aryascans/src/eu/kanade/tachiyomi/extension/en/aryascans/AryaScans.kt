package eu.kanade.tachiyomi.extension.en.aryascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class AryaScans : Madara() {
    override val useNewChapterEndpoint = true

    override val popularMangaUrlSelector = "${super.popularMangaUrlSelector}:not([href=New]):not([target=_self])"
}
