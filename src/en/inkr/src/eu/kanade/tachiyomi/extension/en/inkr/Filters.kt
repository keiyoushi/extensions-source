package eu.kanade.tachiyomi.extension.en.inkr

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Western comics", "western-comics"),
        ),
    )

internal class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

internal class GenreFilters(genres: List<Pair<String, String>>) :
    Filter.Group<GenreFilter>(
        "Genres",
        genres.sortedBy { it.first }.groupBy {
            val c = it.first.firstOrNull()?.uppercase()
            when {
                c == null || c !in "A".."Z" -> "0-9"
                else -> c
            }
        }.map { (letter, chunk) ->
            GenreFilter(letter, chunk)
        },
    )

internal class GenreFilter(letter: String, genres: List<Pair<String, String>>) : Filter.Group<GenreCheckBox>(letter, genres.map { GenreCheckBox(it.first, it.second) })

internal class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)

internal open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val selected get() = vals[state].second
}

internal fun defaultFilterList(genres: List<Pair<String, String>> = GENRES) = FilterList(
    TypeFilter(),
    StatusFilter(),
    GenreFilters(genres),
)

// Genre ids from https://comics.inkr.com/sitemap/comics.xml (`/genre/{id}-{slug}/titles`)
internal val GENRES = listOf(
    "Action" to "ik-genre-2",
    "Adult Cast" to "ik-genre-93",
    "Adult Men" to "ik-genre-87",
    "Adult Women" to "ik-genre-138",
    "Adventure" to "ik-genre-8",
    "Age Gap" to "ik-genre-149",
    "Alternative World" to "ik-genre-143",
    "Animals" to "ik-genre-31",
    "Anthropomorphic" to "ik-genre-35",
    "Avant Garde" to "ik-genre-151",
    "BL / Boys Love" to "ik-genre-33",
    "CEOs" to "ik-genre-156",
    "CGDCT" to "ik-genre-94",
    "Childcare" to "ik-genre-95",
    "Childhood Friends" to "ik-genre-150",
    "Cohabitation" to "ik-genre-144",
    "Combat Sports" to "ik-genre-96",
    "Comedy" to "ik-genre-3",
    "Coming of Age" to "ik-genre-147",
    "Crime" to "ik-genre-25",
    "Crossdressing" to "ik-genre-97",
    "Cultivation" to "ik-genre-142",
    "Cyberpunk" to "ik-genre-98",
    "Delinquents" to "ik-genre-99",
    "Detective" to "ik-genre-100",
    "Disability" to "ik-genre-145",
    "Drama" to "ik-genre-12",
    "Ecchi" to "ik-genre-6",
    "Educational" to "ik-genre-101",
    "Family Life" to "ik-genre-148",
    "Fantasy" to "ik-genre-9",
    "Folklore" to "ik-genre-158",
    "Gag Humor" to "ik-genre-102",
    "Gender Bender" to "ik-genre-62",
    "GL / Girls Love" to "ik-genre-30",
    "Gore" to "ik-genre-103",
    "Gourmet" to "ik-genre-91",
    "Harem" to "ik-genre-43",
    "Harem Fight" to "ik-genre-137",
    "Healing" to "ik-genre-107",
    "High Stakes Game" to "ik-genre-104",
    "Historical" to "ik-genre-18",
    "Historical Fiction" to "ik-genre-59",
    "Horror" to "ik-genre-16",
    "Idols (Female)" to "ik-genre-105",
    "Idols (Male)" to "ik-genre-106",
    "Individual Sport" to "ik-genre-157",
    "Isekai" to "ik-genre-38",
    "Kids" to "ik-genre-141",
    "LGBTQI" to "ik-genre-32",
    "Love Polygon" to "ik-genre-108",
    "Magic" to "ik-genre-10",
    "Magical Girls" to "ik-genre-110",
    "Magical Sex Shift" to "ik-genre-109",
    "Married Life" to "ik-genre-155",
    "Martial Arts" to "ik-genre-4",
    "Mature" to "ik-genre-58",
    "Mecha" to "ik-genre-15",
    "Medical" to "ik-genre-111",
    "Memoir" to "ik-genre-112",
    "Military" to "ik-genre-41",
    "Music" to "ik-genre-21",
    "Mystery" to "ik-genre-24",
    "Mythology" to "ik-genre-113",
    "Neighbors" to "ik-genre-160",
    "Omegaverse" to "ik-genre-152",
    "One Shot" to "ik-genre-23",
    "Organized Crime" to "ik-genre-114",
    "Otaku Culture" to "ik-genre-115",
    "Parody" to "ik-genre-60",
    "Performing Arts" to "ik-genre-116",
    "Pirates" to "ik-genre-154",
    "Political" to "ik-genre-136",
    "Psychological" to "ik-genre-11",
    "Racing" to "ik-genre-118",
    "Reincarnation" to "ik-genre-119",
    "Religion" to "ik-genre-159",
    "Revenge" to "ik-genre-146",
    "Reverse Harem" to "ik-genre-120",
    "Romance" to "ik-genre-5",
    "Romantic Subtext" to "ik-genre-121",
    "Samurai" to "ik-genre-122",
    "School" to "ik-genre-7",
    "Sci-Fi" to "ik-genre-27",
    "Showbiz" to "ik-genre-123",
    "Shoujo Ai" to "ik-genre-28",
    "Shounen Ai" to "ik-genre-19",
    "Slice of Life" to "ik-genre-13",
    "Space" to "ik-genre-124",
    "Sports" to "ik-genre-20",
    "Steampunk" to "ik-genre-135",
    "Strategy Game" to "ik-genre-125",
    "Super Power" to "ik-genre-126",
    "Superhero" to "ik-genre-29",
    "Supernatural" to "ik-genre-1",
    "Survival" to "ik-genre-127",
    "Suspense" to "ik-genre-92",
    "Team Sports" to "ik-genre-128",
    "Teen Boys" to "ik-genre-140",
    "Teen Girls" to "ik-genre-139",
    "Thriller" to "ik-genre-17",
    "Time Travel" to "ik-genre-129",
    "TL (Teens' Love)" to "ik-genre-88",
    "Vampires" to "ik-genre-22",
    "Video Game" to "ik-genre-131",
    "Villainess" to "ik-genre-132",
    "Visual Arts" to "ik-genre-133",
    "Workplace" to "ik-genre-134",
    "Xuanhuan" to "ik-genre-34",
    "Yaoi" to "ik-genre-14",
    "Yuri" to "ik-genre-39",
    "Zombies" to "ik-genre-153",
)
