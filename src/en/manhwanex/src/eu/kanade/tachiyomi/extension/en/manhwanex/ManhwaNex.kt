package eu.kanade.tachiyomi.extension.en.manhwanex

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaNex :
    Madara(
        "ManhwaNex",
        "https://manhwanex.com",
        "en",
    ) {
    override val useNewChapterEndpoint = true

    override val statusFilterOptions = super.statusFilterOptions + mapOf("Upcoming" to "upcoming")
}
