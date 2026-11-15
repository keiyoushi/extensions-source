package eu.kanade.tachiyomi.extension.en.scansgg

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter : Filter.Group<TypeCheckBox>("Type", types.map { TypeCheckBox(it.first, it.second) })
class TypeCheckBox(name: String, val id: Int) : Filter.CheckBox(name)

private val types = listOf(
    Pair("Comic", 1),
    Pair("Manga", 2),
    Pair("Manhwa", 3),
    Pair("Manhua", 4),
    Pair("Webtoon", 5),
)

class StatusFilter : Filter.Group<StatusCheckBox>("Status", statuses.map { StatusCheckBox(it.first, it.second) })
class StatusCheckBox(name: String, val id: Int) : Filter.CheckBox(name)

private val statuses = listOf(
    Pair("Ongoing", 1),
    Pair("Completed", 2),
    Pair("Hiatus", 3),
    Pair("Cancelled", 4),
    Pair("Dropped", 5),
)

class TagFilter : Filter.Group<TagCheckBox>("Tags", tagsMap.map { TagCheckBox(it.value, it.key) })
class TagCheckBox(name: String, val id: Int) : Filter.CheckBox(name)

val tagsMap = mapOf(
    49 to "Regression", 48 to "Male Protagonist", 47 to "Survival",
    46 to "Avant Garde", 45 to "Award Winning", 44 to "Lolicon",
    43 to "Mahou Shoujo", 42 to "Doujinshi", 41 to "Girls Love",
    40 to "Hentai", 39 to "Mecha", 38 to "Shotacon", 37 to "Ecchi",
    36 to "Music", 35 to "Smut", 34 to "Erotica", 33 to "Adult",
    32 to "Gourmet", 31 to "Yuri", 30 to "Shoujo Ai", 29 to "Yaoi",
    28 to "Shounen Ai", 27 to "Boys Love", 26 to "Harem", 25 to "Tragedy",
    24 to "Gender Bender", 23 to "Suspense", 22 to "Psychological",
    21 to "Mature", 20 to "Horror", 19 to "Mystery", 18 to "Martial Arts",
    17 to "Sci-fi", 16 to "Adventure", 15 to "Supernatural", 14 to "Sports",
    13 to "Shounen", 12 to "Historical", 11 to "Seinen", 10 to "Action",
    9 to "Josei", 8 to "Thriller", 7 to "School Life", 6 to "Slice Of Life",
    5 to "Drama", 4 to "Comedy", 3 to "Shoujo", 2 to "Romance", 1 to "Fantasy",
)
