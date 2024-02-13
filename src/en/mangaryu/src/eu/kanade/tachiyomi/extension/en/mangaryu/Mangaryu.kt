package eu.kanade.tachiyomi.extension.en.mangaryu

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Mangaryu : Madara("Mangaryu", "https://mangaryu.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
