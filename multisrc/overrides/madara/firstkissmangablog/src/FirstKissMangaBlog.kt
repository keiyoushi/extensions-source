package eu.kanade.tachiyomi.extension.en.firstkissmangablog

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FirstKissMangaBlog : Madara("1stKissManga.blog", "https://1stkissmanga.blog", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
