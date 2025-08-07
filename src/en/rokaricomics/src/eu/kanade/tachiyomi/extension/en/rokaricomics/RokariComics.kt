package eu.kanade.tachiyomi.extension.en.rokaricomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList

class RokariComics : MangaThemesia(
    "RokariComics",
    "https://rokaricomics.com",
    "en",
) {
    override fun chapterListSelector() = "#chapterlist li:has(div.chbox):has(div.eph-num):has(a[href])"

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().filterNot { it is AuthorFilter || it is YearFilter }
        return FilterList(filters)
    }
}
