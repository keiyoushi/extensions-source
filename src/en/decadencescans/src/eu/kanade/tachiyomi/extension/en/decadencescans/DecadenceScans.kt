package eu.kanade.tachiyomi.extension.en.decadencescans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class DecadenceScans : Madara("Decadence Scans", "https://reader.decadencescans.com", "en") {
    override val useNewChapterEndpoint: Boolean = true
}
