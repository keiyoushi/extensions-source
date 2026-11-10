package eu.kanade.tachiyomi.extension.en.hentaifull

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

class CategoryFilter :
    Filter.Select<String>(
        "Category",
        CATEGORIES.map { it.first }.toTypedArray(),
    ) {
    val selected get() = CATEGORIES[state].second

    companion object {
        private val CATEGORIES = listOf(
            "All" to "",
            "Manhwa" to "category/manhwa",
            "Manga" to "category/manga",
            "Doujinshi" to "category/doujinshi",
            "Artist CG" to "category/artist-cg",
            "Porn Comics" to "category/porn-comics",
            "Western" to "category/western",
            "Non-H" to "category/non-h",
        )
    }
}

@Serializable
class Tag(val name: String, val slug: String)

class TagFilter(private val tags: List<Tag>) :
    Filter.Select<String>(
        "Tag (overrides Category)",
        arrayOf("Any") + tags.map { it.name },
    ) {
    val selected get() = tags.getOrNull(state - 1)?.slug
}
