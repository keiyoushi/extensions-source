package eu.kanade.tachiyomi.extension.all.mangaball

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter<T>(
    name: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class TriStateFilter<T>(name: String, val value: T) : Filter.TriState(name)

abstract class TriStateGroupFilter<T>(
    name: String,
    options: List<Pair<String, T>>,
) : Filter.Group<TriStateFilter<T>>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class SortFilter :
    SelectFilter<String>(
        "Sort By",
        options = listOf(
            "Lastest Updated Chapters" to "updated_chapters_desc",
            "Oldest Updated Chapters" to "updated_chapters_asc",
            "Lastest Created" to "created_at_desc",
            "Oldest Created" to "created_at_asc",
            "Title A-Z" to "name_asc",
            "Title Z-A" to "name_desc",
            "Views High to Low" to "views_desc",
            "Views Low to High" to "views_asc",
        ),
    )

class ContentFilter :
    TriStateGroupFilter<String>(
        "Content",
        options = listOf(
            "Gore" to "685148d115e8b86aae68e4f3",
            "Sexual Violence" to "685146c5f3ed681c80f257e7",
        ),
    )

class FormatFilter :
    TriStateGroupFilter<String>(
        "Format",
        options = listOf(
            "4-Koma" to "685148d115e8b86aae68e4ec",
            "Adaptation" to "685148cf15e8b86aae68e4de",
            "Anthology" to "685148e915e8b86aae68e558",
            "Award Winning" to "685148fe15e8b86aae68e5a7",
            "Doujinshi" to "6851490e15e8b86aae68e5da",
            "Fan Colored" to "6851498215e8b86aae68e704",
            "Full Color" to "685148d615e8b86aae68e502",
            "Long Strip" to "685148d915e8b86aae68e517",
            "Official Colored" to "6851493515e8b86aae68e64a",
            "Oneshot" to "685148eb15e8b86aae68e56c",
            "Self-Published" to "6851492e15e8b86aae68e633",
            "Web Comic" to "685148d715e8b86aae68e50d",
        ),
    )

class GenreFilter :
    TriStateGroupFilter<String>(
        "Genre",
        options = listOf(
            "Action" to "685146c5f3ed681c80f257e3",
            "Adult" to "689371f0a943baf927094f03",
            "Adventure" to "685146c5f3ed681c80f257e6",
            "Boys' Love" to "685148ef15e8b86aae68e573",
            "Comedy" to "685146c5f3ed681c80f257e5",
            "Crime" to "685148da15e8b86aae68e51f",
            "Drama" to "685148cf15e8b86aae68e4dd",
            "Ecchi" to "6892a73ba943baf927094e37",
            "Fantasy" to "685146c5f3ed681c80f257ea",
            "Girls' Love" to "685148da15e8b86aae68e524",
            "Historical" to "685148db15e8b86aae68e527",
            "Horror" to "685148da15e8b86aae68e520",
            "Isekai" to "685146c5f3ed681c80f257e9",
            "Magical Girls" to "6851490d15e8b86aae68e5d4",
            "Mature" to "68932d11a943baf927094e7b",
            "Mecha" to "6851490c15e8b86aae68e5d2",
            "Medical" to "6851494e15e8b86aae68e66e",
            "Mystery" to "685148d215e8b86aae68e4f4",
            "Philosophical" to "685148e215e8b86aae68e544",
            "Psychological" to "685148d715e8b86aae68e507",
            "Romance" to "685148cf15e8b86aae68e4db",
            "Sci-Fi" to "685148cf15e8b86aae68e4da",
            "Shounen Ai" to "689f0ab1f2e66744c6091524",
            "Slice of Life" to "685148d015e8b86aae68e4e3",
            "Smut" to "689371f2a943baf927094f04",
            "Sports" to "685148f515e8b86aae68e588",
            "Superhero" to "6851492915e8b86aae68e61c",
            "Thriller" to "685148d915e8b86aae68e51e",
            "Tragedy" to "685148db15e8b86aae68e529",
            "User Created" to "68932c3ea943baf927094e77",
            "Wuxia" to "6851490715e8b86aae68e5c3",
            "Yaoi" to "68932f68a943baf927094eaa",
            "Yuri" to "6896a885a943baf927094f66",
        ),
    )

class OriginFilter :
    TriStateGroupFilter<String>(
        "Origin",
        options = listOf(
            "Comic" to "68ecab8507ec62d87e62780f",
            "Manga" to "68ecab1e07ec62d87e627806",
            "Manhua" to "68ecab4807ec62d87e62780b",
            "Manhwa" to "68ecab3b07ec62d87e627809",
        ),
    )

class ThemeFilter :
    TriStateGroupFilter<String>(
        "Theme",
        options = listOf(
            "Aliens" to "6851490d15e8b86aae68e5d5",
            "Animals" to "685148e715e8b86aae68e54b",
            "Comics" to "68bf09ff8fdeab0b6a9bc2b7",
            "Cooking" to "685148d215e8b86aae68e4f8",
            "Crossdressing" to "685148df15e8b86aae68e534",
            "Delinquents" to "685148d915e8b86aae68e519",
            "Demons" to "685146c5f3ed681c80f257e4",
            "Genderswap" to "685148d715e8b86aae68e505",
            "Ghosts" to "685148d615e8b86aae68e501",
            "Gyaru" to "685148d015e8b86aae68e4e8",
            "Harem" to "685146c5f3ed681c80f257e8",
            "Hentai" to "68bfceaf4dbc442a26519889",
            "Incest" to "685148f215e8b86aae68e584",
            "Loli" to "685148d715e8b86aae68e506",
            "Mafia" to "685148d915e8b86aae68e518",
            "Magic" to "685148d715e8b86aae68e509",
            "Manhwa 18+" to "68f5f5ce5f29d3c1863dec3a",
            "Martial Arts" to "6851490615e8b86aae68e5c2",
            "Military" to "685148e215e8b86aae68e541",
            "Monster Girls" to "685148db15e8b86aae68e52c",
            "Monsters" to "685146c5f3ed681c80f257e2",
            "Music" to "685148d015e8b86aae68e4e4",
            "Ninja" to "685148d715e8b86aae68e508",
            "Office Workers" to "685148d315e8b86aae68e4fd",
            "Police" to "6851498815e8b86aae68e714",
            "Post-Apocalyptic" to "685148e215e8b86aae68e540",
            "Reincarnation" to "685146c5f3ed681c80f257e1",
            "Reverse Harem" to "685148df15e8b86aae68e533",
            "Samurai" to "6851490415e8b86aae68e5b9",
            "School Life" to "685148d015e8b86aae68e4e7",
            "Shota" to "685148d115e8b86aae68e4ed",
            "Supernatural" to "685148db15e8b86aae68e528",
            "Survival" to "685148cf15e8b86aae68e4dc",
            "Time Travel" to "6851490c15e8b86aae68e5d1",
            "Traditional Games" to "6851493515e8b86aae68e645",
            "Vampires" to "685148f915e8b86aae68e597",
            "Video Games" to "685148e115e8b86aae68e53c",
            "Villainess" to "6851492115e8b86aae68e602",
            "Virtual Reality" to "68514a1115e8b86aae68e83e",
            "Zombies" to "6851490c15e8b86aae68e5d3",
        ),
    )

class TagIncludeMode :
    SelectFilter<String>(
        "Tag Include Mode",
        options = listOf(
            "AND" to "and",
            "OR" to "or",
        ),
    )

class TagExcludeMode :
    SelectFilter<String>(
        "Tag Exclude Mode",
        options = listOf(
            "AND" to "and",
            "OR" to "or",
        ),
    )

class DemographicFilter :
    SelectFilter<String>(
        "Magazine Demographic",
        options = listOf(
            "Any" to "any",
            "Shounen" to "shounen",
            "Shoujo" to "shoujo",
            "Seinen" to "seinen",
            "Josei" to "josei",
            "Yuri" to "yuri",
            "Yaoi" to "yaoi",
        ),
    )

class StatusFilter :
    SelectFilter<String>(
        "Publication Status",
        options = listOf(
            "Any" to "any",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
        ),
    )
