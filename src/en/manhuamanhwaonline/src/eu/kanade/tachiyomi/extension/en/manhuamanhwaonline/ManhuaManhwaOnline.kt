package eu.kanade.tachiyomi.extension.en.manhuamanhwaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaManhwaOnline : Madara("ManhuaManhwa.online", "https://manhuamanhwa.online", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
