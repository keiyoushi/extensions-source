package eu.kanade.tachiyomi.extension.en.manhuaus

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaUS : Madara("ManhuaUS", "https://manhuaus.com", "en") {

    override val useNewChapterEndpoint: Boolean = true

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
