package eu.kanade.tachiyomi.extension.id.riztranslation

import eu.kanade.tachiyomi.source.model.Filter

class HasChapterFilter : Filter.CheckBox("Hanya yang memiliki chapter", false)

class TypeFilter :
    Filter.Select<String>(
        "Tipe",
        arrayOf("Semua", "Manga", "Web Manga"),
    )

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("Semua", "Ongoing", "Completed", "Oneshot"),
    )

class SortFilter :
    Filter.Sort(
        "Urutkan",
        arrayOf("Update Terakhir", "Ditambahkan", "A-Z"),
        Selection(0, false),
    )

private val genres = listOf(
    Pair("Action", "10"),
    Pair("Adventure", "11"),
    Pair("Comedy", "12"),
    Pair("Drama", "1"),
    Pair("Fantasy", "9"),
    Pair("Isekai", "3"),
    Pair("Lucid Dream", "13"),
    Pair("Mysteri", "4"),
    Pair("Romance", "2"),
    Pair("School Life", "8"),
    Pair("Sci-Fi", "14"),
    Pair("Slice of Life", "6"),
    Pair("Supernatural", "24"),
    Pair("Time Travel", "19"),
    Pair("Tragedy", "5"),
)

class GenreFilter : Filter.Group<GenreCheckBox>("Genre", genres.map { GenreCheckBox(it.first, it.second) })

class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)
