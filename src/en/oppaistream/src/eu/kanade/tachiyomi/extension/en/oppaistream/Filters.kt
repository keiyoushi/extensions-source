package eu.kanade.tachiyomi.extension.en.oppaistream

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    fun selectedValue() = vals[state].second
}

internal class OrderByFilter(defaultOrder: String? = null) :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("", ""),
            Pair("A-Z", "az"),
            Pair("Z-A", "za"),
            Pair("Recently Released", "recent"),
            Pair("Oldest Releases", "old"),
            Pair("Most Views", "views"),
            Pair("Highest Rated", "rating"),
            Pair("Recently Uploaded", "uploaded"),
        ),
        defaultOrder,
    )

internal class Genre(name: String, val value: String) : Filter.TriState(name)

internal class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

internal fun getGenreList(): List<Genre> = listOf(
    Genre("Adventure", "adventure"),
    Genre("Beach", "beach"),
    Genre("Blackmail", "blackmail"),
    Genre("Cheating", "cheating"),
    Genre("Comedy", "comedy"),
    Genre("Cooking", "cooking"),
    Genre("Drama", "drama"),
    Genre("Fantasy", "fantasy"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Incest", "incest"),
    Genre("Mind Break", "mindbreak"),
    Genre("Mind Control", "mindcontrol"),
    Genre("Monster", "monster"),
    Genre("Mystery", "mystery"),
    Genre("NTR", "ntr"),
    Genre("Psychological", "psychological"),
    Genre("Rape", "rape"),
    Genre("Reverse Rape", "reverserape"),
    Genre("Romance", "romance"),
    Genre("School Life", "schoollife"),
    Genre("Sci-fi", "sci-fi"),
    Genre("Secret Relationship", "secretrelationship"),
    Genre("Slice of Life", "sliceoflife"),
    Genre("Smut", "smut"),
    Genre("Sports", "sports"),
    Genre("Supernatural", "supernatural"),
    Genre("Tragedy", "tragedy"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
    Genre("Big Boobs", "bigboobs"),
    Genre("Black Hair", "blackhair"),
    Genre("Blonde Hair", "blondehair"),
    Genre("Blue Hair", "bluehair"),
    Genre("Brown Hair", "brownhair"),
    Genre("Cosplay", "cosplay"),
    Genre("Dark Skin", "darkskin"),
    Genre("Demon", "demon"),
    Genre("Dominant Girl", "dominantgirl"),
    Genre("Elf", "elf"),
    Genre("Futanari", "futanari"),
    Genre("Glasses", "glasses"),
    Genre("Green Hair", "greenhair"),
    Genre("Gyaru", "gyaru"),
    Genre("Inverted Nipples", "invertednipples"),
    Genre("Loli", "loli"),
    Genre("Maid", "maid"),
    Genre("Milf", "milf"),
    Genre("Nekomimi", "nekomimi"),
    Genre("Nurse", "nurse"),
    Genre("Pink Hair", "pinkhair"),
    Genre("Pregnant", "pregnant"),
    Genre("Purple Hair", "purplehair"),
    Genre("Red Hair", "redhair"),
    Genre("School Girl", "schoolgirl"),
    Genre("Short Hair", "shorthair"),
    Genre("Small Boobs", "smallboobs"),
    Genre("Succubus", "succubus"),
    Genre("Swimsuit", "swimsuit"),
    Genre("Teacher", "teacher"),
    Genre("Tsundere", "tsundere"),
    Genre("Vampire", "vampire"),
    Genre("Virgin", "virgin"),
    Genre("White Hair", "whitehair"),
    Genre("Old", "old"),
    Genre("Shota", "shota"),
    Genre("Trap", "trap"),
    Genre("Ugly Bastard", "uglybastard"),
)
