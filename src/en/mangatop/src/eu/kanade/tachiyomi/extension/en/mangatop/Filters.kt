package eu.kanade.tachiyomi.extension.en.mangatop

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Group<UriMultiSelectOption>(name, vals.map { UriMultiSelectOption(it.first, it.second) }), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            builder.addQueryParameter(param, it.value)
        }
    }
}

class TypeFilter : UriMultiSelectFilter(
    "Type",
    "types[]",
    arrayOf(
        Pair("Manga", "1"),
        Pair("Novel", "2"),
        Pair("One Shot", "3"),
        Pair("Doujinshi", "4"),
        Pair("Manhwa", "5"),
        Pair("Manhua", "6"),
        Pair("OEL", "7"),
        Pair("Light Novel", "8"),
    ),
)

class GenreFilter : UriMultiSelectFilter(
    "Genre",
    "genres[]",
    arrayOf(
        Pair("Action", "1"),
        Pair("Adventure", "2"),
        Pair("Avant Garde", "5"),
        Pair("Award Winning", "46"),
        Pair("Boys Love", "28"),
        Pair("Comedy", "4"),
        Pair("Drama", "8"),
        Pair("Fantasy", "10"),
        Pair("Girls Love", "26"),
        Pair("Gourmet", "47"),
        Pair("Horror", "14"),
        Pair("Mystery", "7"),
        Pair("Romance", "22"),
        Pair("Sci-Fi", "24"),
        Pair("Slice of Life", "36"),
        Pair("Sports", "30"),
        Pair("Supernatural", "37"),
        Pair("Suspense", "45"),
        Pair("Ecchi", "9"),
        Pair("Erotica", "49"),
        Pair("Hentai", "12"),
        Pair("Adult Cast", "50"),
        Pair("Anthropomorphic", "51"),
        Pair("CGDCT", "52"),
        Pair("Childcare", "53"),
        Pair("Combat Sports", "54"),
        Pair("Crossdressing", "44"),
        Pair("Delinquents", "55"),
        Pair("Detective", "39"),
        Pair("Educational", "56"),
        Pair("Gag Humor", "57"),
        Pair("Gore", "58"),
        Pair("Harem", "35"),
        Pair("High Stakes Game", "59"),
        Pair("Historical", "13"),
        Pair("Idols (Female)", "60"),
        Pair("Idols (Male)", "61"),
        Pair("Isekai", "62"),
        Pair("Iyashikei", "63"),
        Pair("Love Polygon", "64"),
        Pair("Magical Sex Shift", "65"),
        Pair("Mahou Shoujo", "66"),
        Pair("Martial Arts", "17"),
        Pair("Mecha", "18"),
        Pair("Medical", "67"),
        Pair("Memoir", "68"),
        Pair("Military", "38"),
        Pair("Music", "19"),
        Pair("Mythology", "6"),
        Pair("Organized Crime", "69"),
        Pair("Otaku Culture", "70"),
        Pair("Parody", "20"),
        Pair("Performing Arts", "71"),
        Pair("Pets", "72"),
        Pair("Psychological", "40"),
        Pair("Racing", "3"),
        Pair("Reincarnation", "73"),
        Pair("Reverse Harem", "74"),
        Pair("Romantic Subtext", "75"),
        Pair("Samurai", "21"),
        Pair("School", "23"),
        Pair("Showbiz", "76"),
        Pair("Space", "29"),
        Pair("Strategy Game", "11"),
        Pair("Super Power", "31"),
        Pair("Survival", "77"),
        Pair("Team Sports", "78"),
        Pair("Time Travel", "79"),
        Pair("Vampire", "32"),
        Pair("Video Game", "80"),
        Pair("Villainess", "81"),
        Pair("Visual Arts", "82"),
        Pair("Workplace", "48"),
        Pair("Josei", "42"),
        Pair("Kids", "15"),
        Pair("Seinen", "41"),
        Pair("Shoujo", "25"),
        Pair("Shounen", "27"),
    ),
)

class StatusFilter : UriMultiSelectFilter(
    "Status",
    "status[]",
    arrayOf(
        Pair("Ongoing", "0"),
        Pair("Completed", "1"),
    ),
)
