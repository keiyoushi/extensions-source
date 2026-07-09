package eu.kanade.tachiyomi.extension.th.singmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source

@Source
abstract class Singmanga : MangaThemesia() {
    override fun getFilterList() = FilterList()
}
