package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.source.model.Filter

class Entry(name: String, val id: String) : Filter.CheckBox(name) {
    constructor(name: String) : this(name, name)
}

sealed class Group(
    name: String,
    val param: String,
    values: List<Entry>,
) : Filter.Group<Entry>(name, values)

sealed class Select(
    name: String,
    val param: String,
    private val valuesMap: Map<String, String>,
) : Filter.Select<String>(name, valuesMap.keys.toTypedArray()) {
    open val selection: String
        get() = valuesMap[values[state]]!!
}

class TypeFilter : Group("Type", "type[]", types)

private val types: List<Entry>
    get() = listOf(
        Entry("Manga", "manga"),
        Entry("One-Shot", "one_shot"),
        Entry("Doujinshi", "doujinshi"),
        Entry("Light-Novel", "light_novel"),
        Entry("Novel", "novel"),
        Entry("Manhwa", "manhwa"),
        Entry("Manhua", "manhua"),
    )

class Genre(name: String, val id: String) : Filter.TriState(name) {
    val selection: String
        get() = (if (isExcluded()) "-" else "") + id
}

class GenresFilter : Filter.Group<Genre>("Genre", genres) {
    val param = "genre[]"

    val combineMode: Boolean
        get() = state.filter { !it.isIgnored() }.size > 1
}

private val genres: List<Genre>
    get() = listOf(
        Genre("Action", "1"),
        Genre("Adventure", "78"),
        Genre("Avant Garde", "3"),
        Genre("Boys Love", "4"),
        Genre("Comedy", "5"),
        Genre("Demons", "77"),
        Genre("Drama", "6"),
        Genre("Ecchi", "7"),
        Genre("Fantasy", "79"),
        Genre("Girls Love", "9"),
        Genre("Gourmet", "10"),
        Genre("Harem", "11"),
        Genre("Horror", "530"),
        Genre("Isekai", "13"),
        Genre("Iyashikei", "531"),
        Genre("Josei", "15"),
        Genre("Kids", "532"),
        Genre("Magic", "539"),
        Genre("Mahou Shoujo", "533"),
        Genre("Martial Arts", "534"),
        Genre("Mecha", "19"),
        Genre("Military", "535"),
        Genre("Music", "21"),
        Genre("Mystery", "22"),
        Genre("Parody", "23"),
        Genre("Psychological", "536"),
        Genre("Reverse Harem", "25"),
        Genre("Romance", "26"),
        Genre("School", "73"),
        Genre("Sci-Fi", "28"),
        Genre("Seinen", "537"),
        Genre("Shoujo", "30"),
        Genre("Shounen", "31"),
        Genre("Slice of Life", "538"),
        Genre("Space", "33"),
        Genre("Sports", "34"),
        Genre("Super Power", "75"),
        Genre("Supernatural", "76"),
        Genre("Suspense", "37"),
        Genre("Thriller", "38"),
        Genre("Vampire", "39"),
    )

class StatusFilter : Group("Status", "status[]", statuses)

private val statuses: List<Entry>
    get() = listOf(
        Entry("Completed", "completed"),
        Entry("Releasing", "releasing"),
        Entry("On Hiatus", "on_hiatus"),
        Entry("Discontinued", "discontinued"),
        Entry("Not Yet Published", "info"),
    )

class YearFilter : Group("Year", "year[]", years)

private val years: List<Entry>
    get() = listOf(
        Entry("2023"),
        Entry("2022"),
        Entry("2021"),
        Entry("2020"),
        Entry("2019"),
        Entry("2018"),
        Entry("2017"),
        Entry("2016"),
        Entry("2015"),
        Entry("2014"),
        Entry("2013"),
        Entry("2012"),
        Entry("2011"),
        Entry("2010"),
        Entry("2009"),
        Entry("2008"),
        Entry("2007"),
        Entry("2006"),
        Entry("2005"),
        Entry("2004"),
        Entry("2003"),
        Entry("2000s"),
        Entry("1990s"),
        Entry("1980s"),
        Entry("1970s"),
        Entry("1960s"),
        Entry("1950s"),
        Entry("1940s"),
    )

class ChapterCountFilter : Select("Chapter Count", "minchap", chapterCounts)

private val chapterCounts
    get() = mapOf(
        "Any" to "",
        "At least 1 chapter" to "1",
        "At least 3 chapters" to "3",
        "At least 5 chapters" to "5",
        "At least 10 chapters" to "10",
        "At least 20 chapters" to "20",
        "At least 30 chapters" to "30",
        "At least 50 chapters" to "50",
    )

class SortFilter : Select("Sort", "sort", orders)

private val orders
    get() = mapOf(
        "Trending" to "trending",
        "Recently updated" to "recently_updated",
        "Recently added" to "recently_added",
        "Release date" to "release_date",
        "Name A-Z" to "title_az",
        "Score" to "scores",
        "MAL score" to "mal_scores",
        "Most viewed" to "most_viewed",
        "Most favourited" to "most_favourited",
    )
