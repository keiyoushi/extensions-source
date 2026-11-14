package eu.kanade.tachiyomi.extension.id.kumopoi

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(default: String = "latest") :
    Filter.Select<String>(
        "Urutan",
        options.map { it.first }.toTypedArray(),
        options.indexOfFirst { it.second == default }.coerceAtLeast(0),
    ) {
    val selected get() = options[state].second

    companion object {
        private val options = arrayOf(
            "Terbaru" to "latest",
            "Terpopuler" to "popular",
            "A - Z" to "az",
            "Z - A" to "za",
        )
    }
}

class TypeFilter :
    Filter.Select<String>(
        "Tipe",
        options.map { it.first }.toTypedArray(),
    ) {
    val selected get() = options[state].second

    companion object {
        private val options = arrayOf(
            "Semua" to "",
            "Manhwa (Korea)" to "MANHWA",
            "Manga (Jepang)" to "MANGA",
            "Manhua (China)" to "MANHUA",
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Status",
        options.map { it.first }.toTypedArray(),
    ) {
    val selected get() = options[state].second

    companion object {
        private val options = arrayOf(
            "Semua" to "",
            "Ongoing" to "ONGOING",
            "Completed" to "END",
        )
    }
}

class Genre(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) :
    Filter.Group<Genre>(
        "Genre",
        genres,
    )
