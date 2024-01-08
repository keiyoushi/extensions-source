package eu.kanade.tachiyomi.multisrc.flixscans

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<String>,
) : Filter.Select<String>(
    name,
    options.toTypedArray(),
) {
    val selected get() = options[state]
}

class CheckBoxFilter(
    name: String,
    val id: String,
) : Filter.CheckBox(name)

class GenreFilter(
    name: String,
    private val genres: List<GenreHolder>,
) : Filter.Group<CheckBoxFilter>(
    name,
    genres.map { CheckBoxFilter(it.name.trim(), it.id.toString()) },
) {
    val checked get() = state.filter { it.state }.map { it.id }
}

class MainGenreFilter : SelectFilter(
    "Main Genre",
    listOf(
        "",
        "fantasy",
        "romance",
        "action",
        "drama",
    ),
)

class TypeFilter : SelectFilter(
    "Type",
    listOf(
        "",
        "manhwa",
        "manhua",
        "manga",
        "comic",
    ),
)

class StatusFilter : SelectFilter(
    "Status",
    listOf(
        "",
        "ongoing",
        "completed",
        "droped",
        "onhold",
        "soon",
    ),
)
