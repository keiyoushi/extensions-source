package eu.kanade.tachiyomi.extension.fr.banchanscan

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(genres: List<String>) :
    Filter.Group<Filter.CheckBox>(
        "Genres",
        genres.map { genre -> object : Filter.CheckBox(genre) {} },
    )
