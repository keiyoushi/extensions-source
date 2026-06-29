package eu.kanade.tachiyomi.extension.pt.mangaflix

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Gêneros", genres)
