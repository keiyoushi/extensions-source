package eu.kanade.tachiyomi.multisrc.terrascan

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val query: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)
