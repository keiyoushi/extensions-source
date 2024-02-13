package eu.kanade.tachiyomi.extension.en.girlslovemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class GirlsLoveManga : Madara("Girls Love Manga!", "https://glmanga.com", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
