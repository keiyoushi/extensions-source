package eu.kanade.tachiyomi.extension.en.octopusmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class OctopusManga : Madara("OctopusManga", "https://octopusmanga.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
