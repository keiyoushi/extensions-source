package eu.kanade.tachiyomi.extension.fr.softepsilonscan

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
    "Arts martiaux" to "arts-martiaux",
    "Aventure" to "aventure",
    "Bl" to "bl",
    "Bl soft" to "bl-soft",
    "Combat" to "combat",
    "Comédie" to "comedie",
    "Cuisine" to "cuisine",
    "Culitvation" to "culitvation",
    "Donjon" to "donjon",
    "Dragon" to "dragon",
    "Drame" to "drame",
    "Ecchi" to "ecchi",
    "Fantaisie" to "fantaisie",
    "Fantastique" to "fantastique",
    "Fantasy" to "fantasy",
    "Gender bender" to "gender-bender",
    "Gore" to "gore",
    "Guerre" to "guerre",
    "Guideverse" to "guideverse",
    "Harem" to "harem",
    "Historique" to "historique",
    "Homme bête" to "homme-bete",
    "Horreur" to "horreur",
    "Isekai" to "isekai",
    "Jeux vidéos" to "jeux-videos",
    "Josei" to "josei",
    "Magie" to "magie",
    "Manga" to "manga",
    "Manga bl" to "manga-bl",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Mecha" to "mecha",
    "Médical" to "medical",
    "Moderne" to "moderne",
    "Musique" to "musique",
    "Mystère" to "mystere",
    "Omégaverse" to "omegaverse",
    "One shot" to "one-shot",
    "Parentalité" to "parentalite",
    "Politique" to "politique",
    "Psychologique" to "psychologique",
    "Réaliste" to "realiste",
    "Réincarnation" to "reincarnation",
    "Romance" to "romance",
    "Sci-fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shojo" to "shojo",
    "Shojo ai" to "shojo-ai",
    "Shonen" to "shonen",
    "Shonen ai" to "shonen-ai",
    "Shounen" to "shounen",
    "Sports" to "sports",
    "Surnaturel" to "surnaturel",
    "Survie" to "survie",
    "Thriller" to "thriller",
    "Tragédie" to "tragedie",
    "Tranche de vie" to "tranche-de-vie",
    "Triangle amoureux" to "triangle-amoureux",
    "Vengeance" to "vengeance",
    "Vie professionnelle" to "vie-professionnelle",
    "Vie scolaire" to "vie-scolaire",
    "Voyage temporel" to "voyage-temporel",
    "Webtoon" to "webtoon",
    "Webtoons" to "webtoons",
    "Yaoi" to "yaoi",
    "Yuri soft" to "yuri-soft",
)

class TypeFilter : TriStateGroupFilter("Types", type)

private val type = listOf(
    "Artbook" to "artbook",
    "Bd" to "bd",
    "Comics" to "comics",
    "Doujinshi" to "doujinshi",
    "Graphic novel" to "graphic-novel",
    "Light novel" to "light-novel",
    "Manga" to "manga",
    "Manga (webtoon)" to "manga-webtoon",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
)

class StatusFilter : CheckBoxGroup("Status", status)

private val status = listOf(
    "Ongoing" to "ongoing",
    "Finished" to "finished",
    "Dropped" to "dropped",
    "On Hold" to "onhold",
    "Upcoming" to "upcoming",
)
