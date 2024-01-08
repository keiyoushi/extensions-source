package eu.kanade.tachiyomi.extension.en.firstkissdashmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FirstKissDashManga : Madara("1st Kiss-Manga (unoriginal)", "https://1stkiss-manga.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
