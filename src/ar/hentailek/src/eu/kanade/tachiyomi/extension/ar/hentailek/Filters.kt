package eu.kanade.tachiyomi.extension.ar.hentailek

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal abstract class OptionFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value: String get() = options[state].second
}

private val TYPE_OPTIONS = listOf(
    "كل الأنواع" to "",
    "مانجا" to "manga",
    "مانهوا" to "manhwa",
    "كوميك" to "comic",
)

private val STATUS_OPTIONS = listOf(
    "كل الحالات" to "",
    "مستمرّة" to "ongoing",
    "مكتملة" to "completed",
    "متوقّفة مؤقتاً" to "hiatus",
)

private val SORT_OPTIONS = listOf(
    "الأحدث" to "latest",
    "الأكثر مشاهدةً" to "popular",
    "الأكثر إعجاباً" to "most_liked",
    "أ–ي" to "alpha",
)

private val GENRE_OPTIONS = listOf(
    "كل التصنيفات" to "",
    "Action" to "action",
    "Adult Cast" to "adult-cast",
    "Adventure" to "adventure",
    "Avant Garde" to "avant-garde",
    "Award Winning" to "award-winning",
    "Boys Love" to "boys-love",
    "Comedy" to "comedy",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Erotica" to "erotica",
    "Fantasy" to "fantasy",
    "Gag Humor" to "gag-humor",
    "Girls Love" to "girls-love",
    "Gore" to "gore",
    "Gourmet" to "gourmet",
    "Harem" to "harem",
    "هنتاي" to "hentai",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Kids" to "kids",
    "Mahou Shoujo" to "mahou-shoujo",
    "Martial Arts" to "martial-arts",
    "Mecha" to "mecha",
    "Military" to "military",
    "Music" to "music",
    "Mystery" to "mystery",
    "Mythology" to "mythology",
    "Parody" to "parody",
    "Psychological" to "psychological",
    "Racing" to "racing",
    "Reincarnation" to "reincarnation",
    "Romance" to "romance",
    "Samurai" to "samurai",
    "School" to "school",
    "Sci-Fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Slice of Life" to "slice-of-life",
    "Space" to "space",
    "Sports" to "sports",
    "Strategy Game" to "strategy-game",
    "Super Power" to "super-power",
    "Supernatural" to "supernatural",
    "Suspense" to "suspense",
    "Time Travel" to "time-travel",
    "Vampire" to "vampire",
    "Villainess" to "villainess",
    "Visual Arts" to "visual-arts",
    "Workplace" to "workplace",
)

internal class TypeFilter : OptionFilter("النوع", TYPE_OPTIONS)

internal class StatusFilter : OptionFilter("الحالة", STATUS_OPTIONS)

internal class GenreFilter : OptionFilter("التصنيفات", GENRE_OPTIONS)

internal class SortFilter(initial: String = "latest") : OptionFilter("الترتيب", SORT_OPTIONS) {
    init {
        state = SORT_OPTIONS.indexOfFirst { it.second == initial }.coerceAtLeast(0)
    }
}

internal fun hentaiLekFilters(): FilterList = FilterList(
    TypeFilter(),
    StatusFilter(),
    GenreFilter(),
    SortFilter(),
)
