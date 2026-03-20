package eu.kanade.tachiyomi.extension.th.singmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList

class Singmanga : MangaThemesia("SingManga", "https://www.sing-manga.com", "th") {
    override fun getFilterList() = FilterList()
}
