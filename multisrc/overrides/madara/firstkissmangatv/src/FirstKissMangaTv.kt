package eu.kanade.tachiyomi.extension.en.firstkissmangatv

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FirstKissMangaTv : Madara("1stKissManga.tv", "https://1stkissmanga.tv", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
