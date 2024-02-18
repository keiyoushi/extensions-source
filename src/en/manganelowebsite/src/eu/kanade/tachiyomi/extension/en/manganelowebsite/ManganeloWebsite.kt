package eu.kanade.tachiyomi.extension.en.manganelowebsite

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManganeloWebsite : Madara("Manganelo.website (unoriginal)", "https://manganelo.website", "en") {
    override val useNewChapterEndpoint = false
}
