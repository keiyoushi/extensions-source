package eu.kanade.tachiyomi.extension.fr.softepsilonscan

import eu.kanade.tachiyomi.multisrc.pam.CheckBoxGroup
import eu.kanade.tachiyomi.multisrc.pam.NumberFilter
import eu.kanade.tachiyomi.multisrc.pam.Pam
import eu.kanade.tachiyomi.multisrc.pam.SortFilter
import eu.kanade.tachiyomi.multisrc.pam.TriStateGroupFilter
import eu.kanade.tachiyomi.multisrc.pam.TypeGroupFilter
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement

@Source
abstract class SoftEpsilonScan : Pam() {

    override val popularFilters = FilterList(SortFilter("Trier par", sortValues, Filter.Sort.Selection(3, false)))
    override val latestFilters = FilterList(SortFilter("Trier par", sortValues, Filter.Sort.Selection(2, false)))

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter("Trier par", sortValues),
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
        NumberFilter("Année min", "year_min"),
        NumberFilter("Année max", "year_max"),
        NumberFilter("Chapitres min", "chapters_min"),
        NumberFilter("Chapitres max", "chapters_max"),
    )

    private class GenreFilter : TriStateGroupFilter("Genres", genres)
    private class TypeFilter : TypeGroupFilter("Types", type)
    private class StatusFilter : CheckBoxGroup("Statut", status)
}

private val sortValues = listOf(
    "Nouveautés" to "date",
    "Tendances" to "trending",
    "Mises à jour" to "recently",
    "Plus vues" to "views",
    "A-Z" to "alphabetical",
    "Chapitres" to "chapters",
)

private val genres = listOf(
    "4-koma" to "4-koma",
    "Académie" to "academie",
    "Action" to "action",
    "Adulte" to "adulte",
    "Amitié" to "amitie",
    "Amour" to "amour",
    "Arts martiaux" to "arts-martiaux",
    "Assassin" to "assassin",
    "Aventure" to "aventure",
    "Bl" to "bl",
    "Bl soft" to "bl-soft",
    "Boxe" to "boxe",
    "Boys love" to "boys-love",
    "Chill" to "chill",
    "Combat" to "combat",
    "Comédie" to "comedie",
    "Crime" to "crime",
    "Cuisine" to "cuisine",
    "Culitvation" to "culitvation",
    "Cybernétique" to "cybernetique",
    "Démons" to "demons",
    "Dieu" to "dieu",
    "Donjon" to "donjon",
    "Doujinshi" to "doujinshi",
    "Dragon" to "dragon",
    "Drama" to "drama",
    "Drame" to "drame",
    "Ecchi" to "ecchi",
    "Espionnage" to "espionnage",
    "Fantaisie" to "fantaisie",
    "Fantastique" to "fantastique",
    "Fantasy" to "fantasy",
    "Gender bender" to "gender-bender",
    "Girls love" to "girls-love",
    "Gore" to "gore",
    "Guerre" to "guerre",
    "Guideverse" to "guideverse",
    "Harcèlement" to "harcelement",
    "Harem" to "harem",
    "Hentai" to "hentai",
    "Historique" to "historique",
    "Homme bête" to "homme-bete",
    "Horreur" to "horreur",
    "Inceste" to "inceste",
    "Isekai" to "isekai",
    "Jeux vidéos" to "jeux-videos",
    "Josei" to "josei",
    "Mafia" to "mafia",
    "Magical girls" to "magical-girls",
    "Magie" to "magie",
    "Manga" to "manga",
    "Manga bl" to "manga-bl",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Mature" to "mature",
    "Mecha" to "mecha",
    "Médical" to "medical",
    "Moderne" to "moderne",
    "Monstre" to "monstre",
    "Monstres" to "monstres",
    "Murim" to "murim",
    "Musique" to "musique",
    "Mystère" to "mystere",
    "Necromancer" to "necromancer",
    "Nécromancie" to "necromancie",
    "Omégaverse" to "omegaverse",
    "One shot" to "one-shot",
    "Organisation secrète" to "organisation-secrete",
    "Parentalité" to "parentalite",
    "Parodie" to "parodie",
    "Policier" to "policier",
    "Politique" to "politique",
    "Psychologique" to "psychologique",
    "Réaliste" to "realiste",
    "Realité virtuel" to "realite-virtuel",
    "Regression" to "regression",
    "Réincarnation" to "reincarnation",
    "Returner" to "returner",
    "Romance" to "romance",
    "School life" to "school-life",
    "Science-fiction" to "science-fiction",
    "Seinen" to "seinen",
    "Shojo" to "shojo",
    "Shojo ai" to "shojo-ai",
    "Shonen" to "shonen",
    "Shonen ai" to "shonen-ai",
    "Shounen" to "shounen",
    "Smut" to "smut",
    "Sport" to "sport",
    "Sports" to "sports",
    "Steampunk" to "steampunk",
    "Super héros" to "super-heros",
    "Surnaturel" to "surnaturel",
    "Survie" to "survie",
    "Système" to "systeme",
    "Technologie" to "technologie",
    "Thriller" to "thriller",
    "Tournoi" to "tournoi",
    "Tragédie" to "tragedie",
    "Tragique" to "tragique",
    "Tranche de vie" to "tranche-de-vie",
    "Tranches de vie" to "tranches-de-vie",
    "Transmigration" to "transmigration",
    "Triangle amoureux" to "triangle-amoureux",
    "Vampires" to "vampires",
    "Vengeance" to "vengeance",
    "Vie professionnelle" to "vie-professionnelle",
    "Vie scolaire" to "vie-scolaire",
    "Virtuel world" to "virtuel-world",
    "Voyage temporel" to "voyage-temporel",
    "Webtoon" to "webtoon",
    "Webtoons" to "webtoons",
    "Yandere" to "yandere",
    "Yaoi" to "yaoi",
    "Yuri soft" to "yuri-soft",
)

private val type = listOf(
    "Bd" to "bd",
    "Comics" to "comics",
    "Doujinshi" to "doujinshi",
    "Light novel" to "light-novel",
    "Manga" to "manga",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Webtoon" to "webtoon",
)

private val status = listOf(
    "En cours" to "ongoing",
    "Terminée" to "finished",
    "Abandonnée" to "dropped",
    "En pause" to "onhold",
    "À venir" to "upcoming",
)
