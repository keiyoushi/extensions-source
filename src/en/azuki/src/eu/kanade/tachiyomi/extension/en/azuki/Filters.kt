package eu.kanade.tachiyomi.extension.en.azuki

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class CheckBox(name: String, val value: String) : Filter.CheckBox(name)

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            Pair("Popular", "popular"),
            Pair("Recent Series", "recent_series"),
            Pair("Alphabetical", "alphabetical"),
        ),
    )

class AccessTypeFilter :
    SelectFilter(
        "Access Type",
        arrayOf(
            Pair("Any", ""),
            Pair("Partial Premium", "premium_including_partial"),
            Pair("Premium", "fully_premium"),
            Pair("Ebook", "purchasable"),
        ),
    )

class PublisherFilter :
    SelectFilter(
        "Publisher",
        arrayOf(
            Pair("Any", ""),
            Pair("ABLAZE", "ablaze"),
            Pair("C'moA Comics", "cmoa-comics"),
            Pair("CLLENN", "cllenn"),
            Pair("Coamix Inc.", "coamix"),
            Pair("COMIC ROOM Co., Ltd.", "comic-room-co-ltd"),
            Pair("COMPASS Inc.", "compass-inc"),
            Pair("CORK", "cork"),
            Pair("FUNGUILD (MangaPlaza)", "funguild-mangaplaza"),
            Pair("Futabasha Publishers Ltd.", "futabasha-publishers-ltd"),
            Pair("Futabasha Publishers LTD. (MangaPlaza)", "futabasha-publishers-ltd-mangaplaza"),
            Pair("Glacier Bay Books", "glacier-bay-books"),
            Pair("honcomi", "honcomi"),
            Pair("J-Novel Club", "j-novel-club"),
            Pair("KADOKAWA", "kadokawa"),
            Pair("Kaiten Books", "kaiten-books"),
            Pair("Kaoru Tada/M'z plan/Minato Pro", "kaoru-tada-mz-plan-minato-pro"),
            Pair("Kodansha", "kodansha"),
            Pair("Libre Inc.", "libre-inc"),
            Pair("Manga Box Co., Ltd.", "manga-box-co-ltd"),
            Pair("Manga Mavericks Books", "manga-mavericks-books"),
            Pair("Manga Up!", "manga-up"),
            Pair("Omoi", "azuki"),
            Pair("One Peace Books", "one-peace-books"),
            Pair("PICK UP PRESS", "pick-up-press"),
            Pair("Red String Translations", "red-string-translations"),
            Pair("RIDEON", "rideon"),
            Pair("SHODENSHA Publishing CO., LTD.", "shodensha-publishing-co-ltd"),
            Pair("SHUFU TO SEIKATSU SHA", "shufu-to-seikatsu-sha"),
            Pair("SOZO Comics", "sozo-comics"),
            Pair("Star Fruit Books", "star-fruit-books"),
            Pair("TAIYOHTOSHO Co., Ltd.", "taiyohtosho-co-ltd"),
            Pair("Toii Games (MediBang!)", "toii-games-medibang"),
            Pair("TORICO (MediBang!)", "torico-medibang"),
            Pair("Unknown", "unknown"),
            Pair("VAST Visual", "vast-visual"),
            Pair("Voltage Inc.", "voltage"),
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
