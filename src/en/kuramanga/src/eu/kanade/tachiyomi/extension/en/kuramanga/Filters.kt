package eu.kanade.tachiyomi.extension.en.kuramanga

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(name: String, val vals: Array<String>) : Filter.Select<String>(name, vals)

class StatusFilter(name: String, vals: Array<String>) : SelectFilter(name, vals)

class GenreFilter(name: String, genres: List<Genre>) : Filter.Group<Genre>(name, genres)

class Genre(name: String) : Filter.CheckBox(name)

class AdultFilter(name: String) : Filter.CheckBox(name, true)

internal val statusList = arrayOf("All", "Ongoing", "Completed", "On_hold", "Upcoming")

internal val genreNames = listOf(
    "Action", "Adaptation", "Adult", "Adventure", "BL", "Borderline H", "College life", "Comedy", "Crime", "Drama", "Ecchi", "Explicit Sex", "Fantasy", "GL", "Gender Bender", "Harem", "Historical", "Horror", "Isekai", "Josei", "Loli", "Magic", "Magical", "Manhua", "Manhwa", "Martial Arts", "Mature", "Mystery", "Office Workers", "Psychological", "Reincarnation", "Revenge", "Romance", "School Life", "Sci-Fi", "Seinen", "Shoujo", "Shounen", "Slice of Life", "Smut", "Sport", "Supernatural", "Survival", "Thriller", "Time travel", "Tragedy", "Uncensored", "Vampire", "Violence", "Webtoons", "Yuri",
)
