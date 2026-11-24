package eu.kanade.tachiyomi.extension.en.hentaiforce

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String?>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort",
        listOf("Latest" to null, "Popular" to "popular", "Relevant" to "relevant"),
    )

class CategoryFilter :
    SelectFilter(
        "Category",
        listOf(
            "Any" to null,
            "Manga" to "manga",
            "Doujin" to "doujin",
            "Comic" to "comic",
            "CG Hentai" to "cg-hentai",
            "Image Western Set" to "image-western-set",
        ),
    )

class LanguageFilter :
    SelectFilter(
        "Language",
        listOf(
            "Any" to null,
            "English" to "english",
            "Japanese" to "japanese",
            "Chinese" to "chinese",
            "Korean" to "korean",
            "Spanish" to "spanish",
            "Speechless" to "speechless",
        ),
    )
