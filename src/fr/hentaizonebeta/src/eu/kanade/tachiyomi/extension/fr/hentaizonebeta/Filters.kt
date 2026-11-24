package eu.kanade.tachiyomi.extension.fr.hentaizonebeta

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(default: String = "latest") :
    Filter.Select<String>(
        "Trier par",
        VALUES.map { it.first }.toTypedArray(),
        VALUES.indexOfFirst { it.second == default }.coerceAtLeast(0),
    ) {
    fun toUriPart(): String = VALUES[state].second

    companion object {
        private val VALUES = arrayOf(
            Pair("Dernières sorties", "latest"),
            Pair("Plus vus", "views"),
            Pair("Tendance", "trending"),
            Pair("Mieux notés", "rating"),
            Pair("Ordre alphabétique", "alphabetical"),
        )
    }
}

class TypeFilter : Filter.Select<String>("Type", VALUES.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = VALUES[state].second

    companion object {
        private val VALUES = arrayOf(
            Pair("Tous", ""),
            Pair("Artist CG", "artist cg"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Game CG", "game cg"),
            Pair("Image Set", "image set"),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Misc", "misc"),
            Pair("Western", "western"),
        )
    }
}

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

fun getGenreList(): List<Genre> = listOf(
    Genre("Action", "action"),
    Genre("Adult", "adult"),
    Genre("Adulte", "adulte"),
    Genre("Amitié", "amitie"),
    Genre("Amour", "amour"),
    Genre("Artist CG", "7029-artist-cg"),
    Genre("Aventure", "aventure"),
    Genre("BDSM", "bdsm"),
    Genre("Comédie", "comedie"),
    Genre("Doujinshi", "7032-doujinshi"),
    Genre("Drama", "drama"),
    Genre("Drame", "drame"),
    Genre("Ecchi", "ecchi"),
    Genre("Famille", "famille"),
    Genre("Fantastique", "fantastique"),
    Genre("Fantasy", "fantasy"),
    Genre("Game CG", "7047-game-cg"),
    Genre("Harem", "harem"),
    Genre("Hentai", "hentai"),
    Genre("Historique", "historique"),
    Genre("Horreur", "horreur"),
    Genre("Image Set", "6992-image-set"),
    Genre("Incest", "incest"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Magical Girl", "magical-girl"),
    Genre("Manga", "7052-manga"),
    Genre("Mature", "mature"),
    Genre("MILF", "milf"),
    Genre("Military", "military"),
    Genre("Misc", "7036-misc"),
    Genre("Mystère", "mystere"),
    Genre("NTR", "ntr"),
    Genre("Pornhwa", "pornhwa"),
    Genre("Psychologique", "psychologique"),
    Genre("Romance", "romance"),
    Genre("School Life", "3804-school-life"),
    Genre("Sci-fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Smut", "smut"),
    Genre("Sport", "sport"),
    Genre("Sports", "sports"),
    Genre("Surnaturel", "surnaturel"),
    Genre("Tragédie", "tragedie"),
    Genre("Tranche de vie", "tranche-de-vie"),
    Genre("Vengeance", "vengeance"),
    Genre("Vie Scolaire", "vie-scolaire"),
    Genre("Webtoon", "webtoon"),
    Genre("Western", "6991-western"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)
