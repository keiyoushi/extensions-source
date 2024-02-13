package eu.kanade.tachiyomi.extension.en.manganelobiz

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManganeloBiz : Madara("Manganelo.biz", "https://manganelo.biz", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
