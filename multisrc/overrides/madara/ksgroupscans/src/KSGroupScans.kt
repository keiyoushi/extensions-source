package eu.kanade.tachiyomi.extension.en.ksgroupscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class KSGroupScans : Madara("KSGroupScans", "https://ksgroupscans.com", "en") {
    override val versionId = 2
    override val useNewChapterEndpoint = true
}
