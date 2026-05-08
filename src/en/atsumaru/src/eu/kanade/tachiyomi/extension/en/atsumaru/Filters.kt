package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.Filter

internal class GenreFilter(genres: List<Genre>) :
    Filter.Group<Filter.TriState>(
        "Genres",
        genres.map { genre ->
            object : Filter.TriState(genre.name) {}
        },
    ) {
    val genreIds = genres.map { it.id }
}

internal class TypeFilter(types: List<Type>) :
    Filter.Group<Filter.CheckBox>(
        "Manga Type",
        types.map { type ->
            object : Filter.CheckBox(type.name, false) {}
        },
    ) {
    val ids = types.map { it.id }
}

internal class StatusFilter(statuses: List<Status>) :
    Filter.Group<Filter.CheckBox>(
        "Publishing Status",
        statuses.map { status ->
            object : Filter.CheckBox(status.name, false) {}
        },
    ) {
    val ids = statuses.map { it.id }
}

internal class YearFilter : Filter.Text("Year (e.g., 2024)")

internal class MinChaptersFilter : Filter.Text("Minimum Chapters")

internal class SortFilter :
    Filter.Sort(
        "Sort By",
        arrayOf("Popularity", "Trending", "Date Added", "Release Date", "Top Rated"),
        Selection(0, false),
    ) {
    companion object {
        val VALUES = arrayOf("views:desc", "trending:desc", "dateAdded:desc", "released:desc", "avgRating:desc")
    }
}

internal class AdultFilter : Filter.CheckBox("Show Adult Content", false)

internal class OfficialFilter : Filter.CheckBox("Only Official Translations", false)

internal data class Genre(val name: String, val id: String)

internal data class Type(val name: String, val id: String)

internal data class Status(val name: String, val id: String)

internal fun getGenresList() = listOf(
    Genre("Action", "Ip0"),
    Genre("Adult", "oU1"),
    Genre("Adventure", "wY2"),
    Genre("Avant Garde", "6n3"),
    Genre("Award Winning", "6f4"),
    Genre("Boys Love", "Dw5"),
    Genre("Comedy", "pr6"),
    Genre("Doujinshi", "CA7"),
    Genre("Drama", "ME8"),
    Genre("Ecchi", "Gf9"),
    Genre("Erotica", "2S10"),
    Genre("Fantasy", "yv11"),
    Genre("Gender Bender", "Zw12"),
    Genre("Girls Love", "8613"),
    Genre("Gourmet", "jk14"),
    Genre("Harem", "hg15"),
    Genre("Hentai", "d416"),
    Genre("Historical", "qW17"),
    Genre("Horror", "NH18"),
    Genre("Josei", "Uq19"),
    Genre("Lolicon", "XZ20"),
    Genre("Mahou Shoujo", "n421"),
    Genre("Martial Arts", "XO22"),
    Genre("Mature", "Gi23"),
    Genre("Mecha", "N824"),
    Genre("Music", "Eh25"),
    Genre("Mystery", "Xz26"),
    Genre("Psychological", "FV27"),
    Genre("Romance", "Ex28"),
    Genre("School Life", "Zu29"),
    Genre("Sci-Fi", "3j30"),
    Genre("Seinen", "pw31"),
    Genre("Shotacon", "rv32"),
    Genre("Shoujo", "4W33"),
    Genre("Shoujo Ai", "hM34"),
    Genre("Shounen", "W935"),
    Genre("Shounen Ai", "DE36"),
    Genre("Slice of Life", "YX37"),
    Genre("Smut", "ZB38"),
    Genre("Sports", "NC39"),
    Genre("Supernatural", "hT40"),
    Genre("Suspense", "WM41"),
    Genre("Thriller", "e742"),
    Genre("Tragedy", "tn43"),
    Genre("Yaoi", "7D44"),
    Genre("Yuri", "po45"),
)

internal fun getTypesList() = listOf(
    Type("Manga", "Manga"),
    Type("Manhwa", "Manwha"),
    Type("Manhua", "Manhua"),
    Type("OEL", "OEL"),
)

internal fun getStatusList() = listOf(
    Status("Ongoing", "Ongoing"),
    Status("Completed", "Completed"),
    Status("Hiatus", "Hiatus"),
    Status("Canceled", "Canceled"),
)
