package eu.kanade.tachiyomi.extension.all.mihentai

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList

class Mihentai : MangaThemesia("Mihentai", "https://mihentai.com", "all") {
    private class StatusFilter : SelectFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Publishing", "publishing"),
            Pair("Finished", "finished"),
            Pair("Dropped", "drop"),
        ),
    )

    private class TypeFilter : SelectFilter(
        "Type",
        arrayOf(
            Pair("Default", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Webtoon", "webtoon"),
            Pair("One-Shot", "One-Shot"),
            Pair("Doujin", "doujin"),
        ),
    )

    override fun getFilterList(): FilterList = FilterList(
        listOf(
            StatusFilter(),
            TypeFilter(),
            OrderByFilter(),
            GenreListFilter(getGenreList()),
        ),
    )
}
