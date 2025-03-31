package eu.kanade.tachiyomi.extension.en.manhuarm

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manhuarm : Madara(
    "Manhuarm",
    "https://manhuarm.com",
    "en",
) {
    override val useNewChapterEndpoint: Boolean = true
}
