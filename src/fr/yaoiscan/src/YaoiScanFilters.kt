package eu.kanade.tachiyomi.extension.fr.yaoiscan

import eu.kanade.tachiyomi.source.model.Filter

object Note : Filter.Header("NOTE: Ignored if using text search!")

sealed class Select(
    name: String,
    val param: String,
    values: Array<String>,
) : Filter.Select<String>(name, values) {
    open val selection: String
        get() = if (state == 0) "" else state.toString()
}

class TypeFilter(
    values: Array<String> = types.keys.toTypedArray(),
) : Select("Type", "type", values) {
    override val selection: String
        get() = types[values[state]]!!

    companion object {
        private val types = mapOf(
            "Tout" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Comic" to "comic",
            "Webtoon" to "webtoon",
            "Webtoon FR" to "webtoon fr",
            "Novel" to "novel",
        )
    }
}

class StatusFilter(
    values: Array<String> = statuses.keys.toTypedArray(),
) : Select("Status", "status", values) {
    override val selection: String
        get() = statuses[values[state]]!!

    companion object {
        private val statuses = mapOf(
            "Tout" to "",
            "En Cours" to "ongoing",
            "Terminé" to "completed",
            "En Pause" to "paused",
            "Abandonné" to "hiatus",
        )
    }
}

class SortFilter(
    values: Array<String> = sort.keys.toTypedArray(),
) : Select("Trier par", "sort", values) {
    override val selection: String
        get() = sort[values[state]]!!

    companion object {
        private val sort = mapOf(
            "Par défaut" to "",
            "Mise à jour" to "update",
            "Populaire" to "popular",
            "Ajouté" to "latest",
            "Title [A-Z]" to "title",
            "Title [Z-A]" to "titlereverse",
        )
    }
}

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenresFilter(
    values: List<Genre> = genres,
) : Filter.Group<Genre>("Genres", values) {
    val param = "genre[]"

    companion object {
        private val genres: List<Genre>
            get() = listOf(
                /* <input class="genre-item\s+(?:include)?" type="checkbox" id="genre-\d+" name="genre\[]" value="(\d+)"(?: checked data-value='include')?>\n<label for="genre-\d+">([\w\/éèÉôêï\-\sà]+)<\/label><\/li>(?:<li\s+(?:class='include')?>)? */
                Genre("Action", "1628"),
                Genre("Adult", "1843"),
                Genre("Apocalypse", "2745"),
                Genre("Armée", "1982"),
                Genre("Arts martiaux", "2168"),
                Genre("Aventure", "1781"),
                Genre("BDSM", "1645"),
                Genre("BL Soft", "2203"),
                Genre("Boys Love", "1629"),
                Genre("Bureau", "1649"),
                Genre("Campus", "1804"),
                Genre("Comédie", "1638"),
                Genre("Comedy", "2838"),
                Genre("Dom/Sub", "2436"),
                Genre("Dom/Sub-verse", "3238"),
                Genre("Doujinshi", "3382"),
                Genre("Drama", "2393"),
                Genre("Drame", "1630"),
                Genre("Érotique", "1706"),
                Genre("Fantaisie", "1631"),
                Genre("Fantastique", "1651"),
                Genre("Fantasy", "2394"),
                Genre("Furry", "1632"),
                Genre("Gender Bender", "2416"),
                Genre("Guideverse", "1677"),
                Genre("Hardcore", "1680"),
                Genre("Harem", "1978"),
                Genre("Hentai BL", "3567"),
                Genre("Historical", "2395"),
                Genre("Historique", "1641"),
                Genre("Homme bête", "2133"),
                Genre("Horreur", "1848"),
                Genre("i", "2522"),
                Genre("Idol", "1777"),
                Genre("Idols", "1885"),
                Genre("Incube", "1875"),
                Genre("Jeux vidéo", "1869"),
                Genre("Josei", "2209"),
                Genre("Lycanthropes", "1779"),
                Genre("Mafia", "1850"),
                Genre("Manga BL", "1705"),
                Genre("Manhua", "1901"),
                Genre("Manhwa", "1754"),
                Genre("Mature", "1639"),
                Genre("Millitaire", "2379"),
                Genre("Mini BL", "1836"),
                Genre("Mystère", "1760"),
                Genre("Non-censuré", "1702"),
                Genre("Omégaverse", "1633"),
                Genre("One Shot", "2959"),
                Genre("Oneshot", "3066"),
                Genre("Police", "1905"),
                Genre("Psychological", "3360"),
                Genre("Psychologique", "1643"),
                Genre("Réincarnation", "1992"),
                Genre("Relation toxique", "1926"),
                Genre("Romance", "1634"),
                Genre("Royauté", "2071"),
                Genre("Scantrad", "2424"),
                Genre("School Life", "1719"),
                Genre("Science-Fiction", "1678"),
                Genre("Shônen-aï", "1749"),
                Genre("Shota", "1654"),
                Genre("Shounen", "2839"),
                Genre("Shounen Ai", "1787"),
                Genre("Slice of Life", "1919"),
                Genre("Smut", "1635"),
                Genre("Soft", "1690"),
                Genre("Sombre", "2744"),
                Genre("Sports", "1671"),
                Genre("Sub-Dom", "1773"),
                Genre("Supernatural", "1917"),
                Genre("Surnaturel", "1652"),
                Genre("Thriller", "1797"),
                Genre("Tragédie", "1756"),
                Genre("Tranche de Vie", "1646"),
                Genre("Traumatisme", "2743"),
                Genre("Triangle Amoureux", "1636"),
                Genre("Vampire", "1768"),
                Genre("Vengeance", "3831"),
                Genre("Vie Scolaire", "1647"),
                Genre("Violence", "2284"),
                Genre("Webtoon", "1655"),
                Genre("Webtoon FR", "2297"),
                Genre("Yakuza", "2287"),
                Genre("Yaoi", "1687"),
                Genre("Zombies", "2002"),
            )
    }
}
