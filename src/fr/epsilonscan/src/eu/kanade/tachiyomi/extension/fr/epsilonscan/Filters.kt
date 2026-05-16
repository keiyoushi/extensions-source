package eu.kanade.tachiyomi.extension.fr.epsilonscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class SortFilter(
    selection: Selection = Selection(0, false),
) : Filter.Sort(
    name = "Sort",
    values = sortValues.map { it.second }.toTypedArray(),
    state = selection,
) {
    val sort get() = sortValues[state?.index ?: 0].second
    val ascending get() = state?.ascending ?: false

    companion object {
        val popular = FilterList(SortFilter(Selection(3, false)))
        val latest = FilterList(SortFilter(Selection(2, false)))
    }
}

private val sortValues = listOf(
    "New Series" to "date",
    "Trending" to "trending",
    "Recently Updated" to "recently",
    "Most Views" to "views",
    "A-Z" to "alphabetical",
)

class GenreFilter : TriStateGroupFilter("Genres", genres)

private val genres = listOf(
    "Action" to "action",
    "Ahegao" to "ahegao",
    "Anal" to "anal",
    "Anime bl" to "anime-bl",
    "Arts martiaux" to "arts-martiaux",
    "Aventure" to "aventure",
    "Bdsm" to "bdsm",
    "Bondage" to "bondage",
    "Boys love" to "boys-love",
    "Bureau" to "bureau",
    "Campus" to "campus",
    "Comédie" to "comedie",
    "Comics" to "comics",
    "Cosplay" to "cosplay",
    "Coup d'un soir" to "coup-dun-soir",
    "Dark skin" to "dark-skin",
    "Démon/démone" to "demondemone",
    "Différence d'âge" to "difference-dage",
    "Doujinshi" to "doujinshi",
    "Drame" to "drame",
    "Échangisme" to "echangisme",
    "Elf" to "elf",
    "Espion" to "espion",
    "Exhibitionniste" to "exhibitionniste",
    "Fantaisie" to "fantaisie",
    "Fantastique" to "fantastique",
    "Fétichisme" to "fetichisme",
    "Furry" to "furry",
    "Gangster" to "gangster",
    "Gender bender" to "gender-bender",
    "Girls love" to "girls-love",
    "Gros seins" to "gros-seins",
    "Guideverse" to "guideverse",
    "Hardcore" to "hardcore",
    "Harem" to "harem",
    "Historique" to "historique",
    "Horreur" to "horreur",
    "Hypnose" to "hypnose",
    "Ia" to "ia",
    "Immoral" to "immoral",
    "Isekai" to "isekai",
    "Jeux vidéo" to "jeux-video",
    "Josei" to "josei",
    "Magie" to "magie",
    "Manga bl" to "manga-bl",
    "Manga h" to "manga-h",
    "Manga josei" to "manga-josei",
    "Mature" to "mature",
    "Médical" to "medical",
    "Milf" to "milf",
    "Mini-série" to "mini-serie",
    "Moderne" to "moderne",
    "Muscle" to "muscle",
    "Mystère" to "mystere",
    "Noblesse" to "noblesse",
    "Non-censuré" to "non-censure",
    "Novel" to "novel",
    "Ntr" to "ntr",
    "Omégaverse" to "omegaverse",
    "One shot" to "one-shot",
    "Percing" to "percing",
    "Plan à 3" to "plan-a-3",
    "Pornhwa" to "pornhwa",
    "Professeur" to "professeur",
    "Psychologique" to "psychologique",
    "Réincarnation" to "reincarnation",
    "Romance" to "romance",
    "Science-fiction" to "science-fiction",
    "Showbiz" to "showbiz",
    "Smut" to "smut",
    "Spanking" to "spanking",
    "Sports" to "sports",
    "Succube" to "succube",
    "Surnaturel" to "surnaturel",
    "Système" to "systeme",
    "Thriller" to "thriller",
    "Tragédie" to "tragedie",
    "Tranche de vie" to "tranche-de-vie",
    "Triangle amoureux" to "triangle-amoureux",
    "Tsundere" to "tsundere",
    "Vampire" to "vampire",
    "Vengeance" to "vengeance",
    "Vie scolaire" to "vie-scolaire",
    "Webtoon" to "webtoon",
)

class TypeFilter : TriStateGroupFilter("Types", type)

private val type = listOf(
    "Anime bl" to "anime-bl",
    "Boys love" to "boys-love",
    "Doujinshi" to "doujinshi",
    "Girls love" to "girls-love",
    "Hentai" to "hentai",
    "Josei" to "josei",
    "Manga" to "manga",
    "Manga bl" to "manga-bl",
    "Manga h" to "manga-h",
    "Manga josei" to "manga-josei",
    "Manhwa" to "manhwa",
    "Manwha" to "manwha",
    "Novel" to "novel",
    "Other" to "other",
    "Pornhwa" to "pornhwa",
    "Seinen" to "seinen",
)

class StatusFilter : CheckBoxGroup("Status", status)

private val status = listOf(
    "Ongoing" to "ongoing",
    "Finished" to "finished",
    "Dropped" to "dropped",
    "On Hold" to "onhold",
    "Upcoming" to "upcoming",
)
