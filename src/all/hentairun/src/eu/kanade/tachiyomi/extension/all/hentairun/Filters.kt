package eu.kanade.tachiyomi.extension.all.hentairun

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(state: Int = 0) : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray(), state) {
    val value get() = SORTS[state].second
}

class CategoryFilter : Filter.Group<CategoryCheckBox>("Categories", CATEGORIES.map { (name, value) -> CategoryCheckBox(name, value) }) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class CategoryCheckBox(name: String, val value: String) : Filter.CheckBox(name)

val SORTS = listOf(
    "Latest" to "latest",
    "Popular" to "popular",
    "Top rated" to "top-rated",
    "Downloaded" to "downloaded",
)

const val POPULAR_SORT = 1

private val CATEGORIES = listOf(
    "Manga" to "manga",
    "Doujinshi" to "doujinshi",
    "Western" to "western",
    "Image Set" to "image-set",
    "Artist CG" to "artist-cg",
    "Game CG" to "game-cg",
)
