package eu.kanade.tachiyomi.extension.all.sankakucomplex

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(state: Int = 0) :
    Filter.Select<String>(
        "Sort by",
        SORTS.map { it.first }.toTypedArray(),
        state,
    ) {
    val tag: String get() = SORTS[state].second

    companion object {
        const val DATE = 1

        private val SORTS = listOf(
            "Popularity" to "order:popularity",
            "Date" to "order:date",
            "Quality" to "order:quality",
            "Favorites" to "order:favorites",
            "Recently favorited" to "order:recently_favorited",
            "Recently voted" to "order:recently_voted",
            "Random" to "order:random",
        )
    }
}

class RatingFilter :
    Filter.Select<String>(
        "Rating",
        RATINGS.map { it.first }.toTypedArray(),
    ) {
    val tag: String? get() = RATINGS[state].second

    companion object {
        private val RATINGS = listOf(
            "All" to null,
            "G" to "rating:s",
            "R15+" to "rating:q",
            "R18+" to "rating:e",
        )
    }
}

class LanguageFilter :
    Filter.Select<String>(
        "Language",
        LANGUAGES.map { it.first }.toTypedArray(),
    ) {
    val tag: String? get() = LANGUAGES[state].second

    companion object {
        private val LANGUAGES = listOf(
            "All" to null,
            "English" to "english_language",
            "Japanese" to "japanese_language",
            "Chinese" to "chinese_language",
            "Korean" to "korean_language",
            "Spanish" to "spanish_language",
            "Portuguese" to "portuguese_language",
            "French" to "french_language",
            "German" to "german_language",
            "Italian" to "italian_language",
            "Dutch" to "dutch_language",
            "Thai" to "thai_language",
        )
    }
}

class TagsFilter : Filter.Text("Tags")
