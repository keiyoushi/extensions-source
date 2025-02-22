package eu.kanade.tachiyomi.extension.en.aryascans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class AryaScans : Madara(
    "Arya Scans",
    "https://aryascans.com",
    "en",
) {
    override val useNewChapterEndpoint = true

    override val popularMangaUrlSelector = "${super.popularMangaUrlSelector}:not([href=New]):not([target=_self])"
}
