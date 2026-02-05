package eu.kanade.tachiyomi.extension.en.azuki

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class CheckBox(name: String, val value: String) : Filter.CheckBox(name)

class SortFilter :
    UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Popular", "popular"),
            Pair("Recent Series", "recent_series"),
            Pair("Alphabetical", "alphabetical"),
        ),
    )

class AccessTypeFilter :
    UriPartFilter(
        "Access Type",
        arrayOf(
            Pair("Any", ""),
            Pair("Premium", "fully_premium"),
            Pair("Ebook", "purchasable"),
        ),
    )

class PublisherFilter :
    UriPartFilter(
        "Publisher",
        arrayOf(
            Pair("Any", ""),
            Pair("ABLAZE", "ablaze"),
            Pair("Azuki", "azuki"),
            Pair("CLLENN", "cllenn"),
            Pair("Coamix Inc.", "coamix"),
            Pair("CORK", "cork"),
            Pair("Futabasha Publishers Ltd.", "futabasha-publishers-ltd"),
            Pair("Glacier Bay Books", "glacier-bay-books"),
            Pair("J-Novel Club", "j-novel-club"),
            Pair("KADOKAWA", "kadokawa"),
            Pair("Kaiten Books", "kaiten-books"),
            Pair("Kodansha", "kodansha"),
            Pair("Manga Mavericks Books", "manga-mavericks-books"),
            Pair("Manga Up!", "manga-up"),
            Pair("One Peace Books", "one-peace-books"),
            Pair("SOZO Comics", "sozo-comics"),
            Pair("Star Fruit Books", "star-fruit-books"),
            Pair("Toii Games (MediBang!)", "toii-games-medibang"),
            Pair("TORICO (MediBang!)", "torico-medibang"),
            Pair("Unknown", "unknown"),
            Pair("VAST Visual", "vast-visual"),
            Pair("YUZU Comics", "yuzu-comics"),
        ),
    )

class GenreFilter :
    Filter.Group<CheckBox>(
        "Genres",
        listOf(
            CheckBox("Action", "action"),
            CheckBox("Adventure", "adventure"),
            CheckBox("Comedy", "comedy"),
            CheckBox("Drama", "drama"),
            CheckBox("Ecchi", "ecchi"),
            CheckBox("Fantasy", "fantasy"),
            CheckBox("Harem", "harem"),
            CheckBox("Historical", "historical"),
            CheckBox("Horror", "horror"),
            CheckBox("Josei", "josei"),
            CheckBox("Martial Arts", "martial-arts"),
            CheckBox("Mature", "mature"),
            CheckBox("Mecha", "mecha"),
            CheckBox("Mystery", "mystery"),
            CheckBox("Psychological", "psychological"),
            CheckBox("Romance", "romance"),
            CheckBox("School Life", "school-life"),
            CheckBox("Sci-Fi", "scifi"),
            CheckBox("Seinen", "seinen"),
            CheckBox("Shojo", "shoujo"),
            CheckBox("Shonen", "shounen"),
            CheckBox("Slice of Life", "slice-of-life"),
            CheckBox("Sports", "sports"),
            CheckBox("Supernatural", "supernatural"),
            CheckBox("Tragedy", "tragedy"),
        ),
    )
