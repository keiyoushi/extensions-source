package eu.kanade.tachiyomi.extension.en.manhuascaninfo

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaScanInfo : Madara("ManhuaScan.info (unoriginal)", "https://manhuascan.info", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
