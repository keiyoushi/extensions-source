package eu.kanade.tachiyomi.extension.en.manhuafastnet

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaFastNet : Madara("ManhuaFast.net (unoriginal)", "https://manhuafast.net", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
