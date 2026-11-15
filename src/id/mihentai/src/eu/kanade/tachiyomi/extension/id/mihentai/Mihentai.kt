package eu.kanade.tachiyomi.extension.id.mihentai

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement

@Source
abstract class Mihentai : MangaThemesia() {
    private class StatusFilter :
        SelectFilter(
            "Status",
            arrayOf(
                Pair("All", ""),
                Pair("Publishing", "publishing"),
                Pair("Finished", "finished"),
                Pair("Dropped", "drop"),
            ),
        )

    private class TypeFilter :
        SelectFilter(
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

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOf(
            StatusFilter(),
            TypeFilter(),
        ) + super.getFilterList(data).filter { it is GenreListFilter || it is OrderByFilter },
    )
}
