package eu.kanade.tachiyomi.multisrc.komiko

import eu.kanade.tachiyomi.source.model.Filter

// In the order of the API's sort values
internal class SortFilter : Filter.Select<String>("Trier par", arrayOf("Popularité", "Titre", "Date de parution"))

internal class GenreModeFilter : Filter.Select<String>("Genres cochés", arrayOf("Au moins un", "Tous"))

internal class GenreCheckBox(name: String, val id: Int) : Filter.CheckBox(name)

internal class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreCheckBox>("Genres", genres.map { GenreCheckBox(it.name, it.id) })
