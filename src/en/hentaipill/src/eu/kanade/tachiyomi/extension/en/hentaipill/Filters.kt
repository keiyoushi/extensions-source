package eu.kanade.tachiyomi.extension.en.hentaipill

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort By",
        arrayOf("Latest", "Popular", "Relevant"),
    )

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    fun toUriPart() = vals[state].second
}

class CategoryFilter :
    UriPartFilter(
        "Category",
        arrayOf(
            "Any (Uses Search)" to "",
            "CG Hentai" to "category/cg-hentai",
            "Comic" to "category/comic-hentai",
            "Doujin" to "category/doujin",
            "Manga" to "category/manga",
            "Rising" to "rising",
            "Popular" to "popular",
        ),
    )

class GenreFilter : Filter.Text("Genre (e.g. drunk female)")
class ParodyFilter : Filter.Text("Parody (e.g. fate grand order)")
class CharacterFilter : Filter.Text("Character (e.g. raiden shogun)")
class ArtistFilter : Filter.Text("Artist (e.g. panqlao)")
