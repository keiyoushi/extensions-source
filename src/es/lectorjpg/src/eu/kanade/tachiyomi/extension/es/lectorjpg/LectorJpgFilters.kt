package eu.kanade.tachiyomi.extension.es.lectorjpg

import eu.kanade.tachiyomi.source.model.Filter

class Genre(title: String, val key: String) : Filter.CheckBox(title)
class GenreFilter(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)
