package eu.kanade.tachiyomi.extension.en.mangataro

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Calendar

abstract class SelectFilter<T>(
    name: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class CheckBoxFilter<T>(name: String, val value: T) : Filter.CheckBox(name)

abstract class CheckBoxGroup<T>(
    name: String,
    options: List<Pair<String, T>>,
) : Filter.Group<CheckBoxFilter<T>>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class SearchWithFilters : Filter.CheckBox("Apply filters to Text Search", false)

class TypeFilter : SelectFilter<String?>(
    name = "Type",
    options = listOf(
        "All" to null,
        "Manga" to "Manga",
        "Manhwa" to "Manhwa",
        "Manhua" to "Manhua",
    ),
)

class StatusFilter : SelectFilter<String?>(
    name = "Status",
    options = listOf(
        "All" to null,
        "Completed" to "Completed",
        "Ongoing" to "Ongoing",
    ),
)

class YearFilter : SelectFilter<Int?>(
    name = "Year",
    options = buildList {
        add("All" to null)
        val current = Calendar.getInstance().get(Calendar.YEAR)
        (current downTo 1949).mapTo(this) { it.toString() to it }
    },
)

class TagFilter : CheckBoxGroup<Int>(
    name = "Tags",
    options = tags,
)

class TagFilterMatch : SelectFilter<String>(
    name = "Tag Match",
    options = listOf(
        "Any" to "any",
        "All" to "all",
    ),
)

class SortFilter(
    state: Selection = Selection(0, false),
) : Filter.Sort(
    name = "Sort",
    values = sort.map { it.first }.toTypedArray(),
    state = state,
) {
    private val sortDirection get() = if (state?.ascending == true) {
        "asc"
    } else {
        "desc"
    }
    val selected get() = "${sort[state?.index ?: 0].second}_$sortDirection"

    companion object {
        val popular = FilterList(
            SortFilter(Selection(3, false)),
            TagFilterMatch(),
        )
        val latest = FilterList(
            SortFilter(Selection(0, false)),
            TagFilterMatch(),
        )
    }
}

private val tags = listOf(
    "4-Koma" to 2094,
    "Abandoned Children" to 1050,
    "Ability Steal" to 2386,
    "Absent Parents" to 2397,
    "Academy" to 4012,
    "Accelerated Growth" to 999,
    "Action" to 7,
    "Adaptation" to 1351,
    "Adapted to Anime" to 1000,
    "Adapted to Drama CD" to 5072,
    "Adapted to Game" to 1001,
    "Adapted to Manga" to 5069,
    "Adapted to Manhua" to 963,
    "Adapted to Manhwa" to 1002,
    "Adapted to Visual Novel" to 5070,
    "Adopted Children" to 1058,
    "Adult" to 88,
    "Adult Cast" to 16,
    "Adventure" to 12,
    "Age Regression" to 2861,
    "Alchemy" to 964,
    "Aliens" to 965,
    "Alternate World" to 966,
    "Ancient Times" to 2862,
    "Animals" to 1135,
    "Anthology" to 2096,
    "Anthropomorphic" to 93,
    "Appearance Different from Actual Age" to 5071,
    "Aristocracy" to 1037,
    "Army" to 2387,
    "Army Building" to 1005,
    "Arranged Marriage" to 1047,
    "Arrogant Characters" to 2401,
    "Artbook" to 4551,
    "Artifacts" to 4004,
    "Assassins" to 2863,
    "Avant Garde" to 98,
    "Award Winning" to 17,
    "Battle Academy" to 5279,
    "Battle Competition" to 2388,
    "BD" to 1137,
    "Beast Companions" to 968,
    "Beautiful Female Lead" to 969,
    "Betrayal" to 2864,
    "Blacksmith" to 971,
    "Boys Love" to 80,
    "Boys' Love" to 4237,
    "Brotherhood" to 1038,
    "Business Management" to 2398,
    "Calm Protagonist" to 1006,
    "Cautious Protagonist" to 2392,
    "CGDCT" to 94,
    "Character growth" to 4929,
    "Childcare" to 51,
    "Children's" to 4818,
    "Cold Love Interests" to 2393,
    "Cold Protagonist" to 2394,
    "Combat Sports" to 92,
    "Comedy" to 20,
    "Cooking" to 2118,
    "Crime" to 1753,
    "Crossdressing" to 81,
    "Cruel Characters" to 2395,
    "Cunning Protagonist" to 1039,
    "Cute Stuffs" to 4817,
    "Dark" to 4930,
    "Death of Loved Ones" to 1044,
    "Delinquents" to 52,
    "Demons" to 1376,
    "Dense Protagonist" to 975,
    "Detective" to 58,
    "Devoted love interests" to 4008,
    "Doujinshi" to 79,
    "Dragons" to 1040,
    "Drama" to 8,
    "Drugs" to 1048,
    "Dungeon" to 4795,
    "Dungeons" to 1011,
    "Ecchi" to 40,
    "Educational" to 66,
    "Elemental Magic" to 976,
    "Elves" to 4010,
    "Erotica" to 71,
    "Evolution" to 4931,
    "Family" to 4005,
    "Fantasy" to 2,
    "French" to 108,
    "Full Color" to 34,
    "Gag Humor" to 59,
    "Game" to 36,
    "Game Elements" to 979,
    "Gender Bender" to 106,
    "Genderswap" to 2093,
    "Ghosts" to 1134,
    "Girls Love" to 49,
    "Gore" to 23,
    "Gourmet" to 46,
    "Gyaru" to 4739,
    "Harem" to 64,
    "Hentai" to 82,
    "High Stakes Game" to 76,
    "Historical" to 18,
    "Horror" to 44,
    "Human nonhuman relationship" to 4932,
    "Idols (Female)" to 95,
    "Idols (Male)" to 101,
    "Indonesian" to 109,
    "Isekai" to 3,
    "Iyashikei" to 91,
    "Josei" to 43,
    "Kids" to 107,
    "Kingdoms" to 4006,
    "Level System" to 1020,
    "Light Novel" to 55,
    "Loli" to 4207,
    "Lolicon" to 89,
    "Long Strip" to 1172,
    "Love interest falls in love first" to 4007,
    "Love Polygon" to 41,
    "Love Status Quo" to 100,
    "Mafia" to 2061,
    "Magic" to 35,
    "Magical Girls" to 2095,
    "Magical Sex Shift" to 68,
    "Mahou Shoujo" to 97,
    "Male" to 4933,
    "Manga" to 14,
    "Mangataro Exclusive" to 4792,
    "Manhua" to 33,
    "Manhwa" to 6,
    "Martial Arts" to 4,
    "Master-disciple relationship" to 4013,
    "Mature" to 87,
    "Mecha" to 74,
    "Medical" to 67,
    "Memoir" to 96,
    "Military" to 30,
    "Misunderstandings" to 1023,
    "Monster Girls" to 1414,
    "Monsters" to 37,
    "Multiple POV" to 1024,
    "Murim" to 38,
    "Music" to 83,
    "Mystery" to 24,
    "Mythology" to 53,
    "Ninja" to 4601,
    "OEL" to 103,
    "Office Workers" to 2062,
    "Official Colored" to 4834,
    "One-shot" to 48,
    "Oneshot" to 4210,
    "Organized Crime" to 50,
    "Otaku Culture" to 70,
    "Parody" to 21,
    "Performing Arts" to 72,
    "Pets" to 105,
    "Philosophical" to 63,
    "Police" to 1136,
    "Post-Apocalyptic" to 1557,
    "Psychological" to 26,
    "Racing" to 102,
    "Reincarnated in another world" to 4014,
    "Reincarnation" to 5,
    "Reverse Harem" to 61,
    "Romance" to 29,
    "Romantic Subtext" to 28,
    "Samurai" to 60,
    "School" to 10,
    "School Life" to 73,
    "Sci-Fi" to 15,
    "Seinen" to 19,
    "Self-Published" to 1413,
    "Sexual Violence" to 4116,
    "Shoujo" to 42,
    "Shoujo Ai" to 90,
    "Shounen" to 13,
    "Shounen Ai" to 104,
    "Showbiz" to 54,
    "Skill books" to 4009,
    "Slice of Life" to 47,
    "Smut" to 85,
    "Space" to 84,
    "Sports" to 25,
    "Strategy Game" to 77,
    "Super Power" to 22,
    "Superhero" to 4098,
    "Supernatural" to 9,
    "Survival" to 56,
    "Suspense" to 75,
    "Sword And Magic" to 995,
    "Team Sports" to 27,
    "Thai" to 591,
    "Thriller" to 1341,
    "Time Travel" to 32,
    "Traditional Games" to 4742,
    "Tragedy" to 65,
    "Transported to another world" to 4011,
    "Urban Fantasy" to 110,
    "Vampire" to 45,
    "Vampires" to 2102,
    "Video Game" to 31,
    "Video Games" to 1366,
    "Villainess" to 62,
    "Virtual Reality" to 4485,
    "Visual Arts" to 57,
    "Web Comic" to 1173,
    "Webtoon" to 39,
    "Workplace" to 69,
    "Wuxia" to 1350,
    "Xianxia" to 2391,
    "Xuanhuan" to 962,
    "Yaoi" to 86,
    "Yuri" to 99,
    "Zombies" to 1342,
)

private val sort = listOf(
    "Latest Updates" to "post",
    "Release Date" to "release",
    "Title A-Z" to "title",
    "Popular" to "popular",
)
