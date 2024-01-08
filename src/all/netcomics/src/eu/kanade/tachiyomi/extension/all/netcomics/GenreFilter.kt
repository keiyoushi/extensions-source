package eu.kanade.tachiyomi.extension.all.netcomics

import eu.kanade.tachiyomi.source.model.Filter

internal class GenreFilter(
    values: Array<String> = genres,
) : Filter.Select<String>("Genre", values) {
    override fun toString() = if (state == 0) "" else values[state]

    companion object {
        internal val NOTE = Header("NOTE: can't be used with text search!")

        private val genres = arrayOf(
            "All",
            "BL",
            "Action",
            "Comedy",
            "Romance",
            "Thriller",
            "Drama",
        )
    }
}
