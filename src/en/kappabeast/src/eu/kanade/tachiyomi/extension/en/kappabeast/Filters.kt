package eu.kanade.tachiyomi.extension.en.kappabeast

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Sort By", sorts.map { it.first }.toTypedArray()) {
    val selected get() = sorts[state].second

    companion object {
        private val sorts = arrayOf(
            Pair("Popularity", ""),
            Pair("Latest Updates", "updatedAt:desc"),
            Pair("A-Z Name", "title:asc"),
            Pair("Newest", "createdAt:desc"),
        )
    }
}

class GenreFilter : Filter.Select<String>("Genre", genres.map { it.first }.toTypedArray()) {
    val selected get() = genres[state].second

    companion object {
        private val genres = arrayOf(
            Pair("All", ""),
            Pair("Fantasy", "Fantasy"),
            Pair("Romance", "Romance"),
            Pair("Comedy", "Comedy"),
            Pair("Drama", "Drama"),
            Pair("Thriller", "Thriller"),
            Pair("Action", "Action"),
            Pair("Psychological", "Psychological"),
            Pair("Isekai", "Isekai"),
        )
    }
}

class StatusFilter : Filter.Select<String>("Status", statuses.map { it.first }.toTypedArray()) {
    val selected get() = statuses[state].second

    companion object {
        private val statuses = arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
            Pair("Hiatus", "Hiatus"),
        )
    }
}

class TypeFilter : Filter.Select<String>("Type", types.map { it.first }.toTypedArray()) {
    val selected get() = types[state].second

    companion object {
        private val types = arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
        )
    }
}
