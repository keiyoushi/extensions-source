package eu.kanade.tachiyomi.extension.en.hentaipill

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart() = options[state].second
}

class SortFilter :
    UriPartFilter(
        "Sort by",
        listOf(
            "Latest" to "",
            "Popular" to "popular",
            "Relevant" to "relevant",
        ),
    )

class CategoryFilter :
    UriPartFilter(
        "Category",
        listOf(
            "Popular" to "popular",
            "Rising" to "rising",
            "Latest" to "language/english",
            "Doujin" to "category/doujin",
            "Manga" to "category/manga",
            "Comic" to "category/comic-hentai",
            "CG" to "category/cg-hentai",
        ),
    )

class TagFilter(
    name: String,
    val path: String,
    example: String,
) : Filter.Text("$name (e.g. $example)")
