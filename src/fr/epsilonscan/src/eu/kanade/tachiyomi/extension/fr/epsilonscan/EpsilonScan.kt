package eu.kanade.tachiyomi.extension.fr.epsilonscan

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
abstract class EpsilonScan : Pam() {

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
    "Action" to "action",
    "Ahegao" to "ahegao",
    "Anal" to "anal",
    "Arts martiaux" to "arts-martiaux",
    "Aventure" to "aventure",
    "Bdsm" to "bdsm",
    "Big penis" to "big-penis",
    "Blowjob" to "blowjob",
    "Bondage" to "bondage",
    "Boulot" to "boulot",
    "Boys love" to "boys-love",
    "Bureau" to "bureau",
    "Comédie" to "comedie",
    "Comics" to "comics",
    "Cosplay" to "cosplay",
    "Coup d'un soir" to "coup-dun-soir",
    "Créature" to "creature",
    "Dark skin" to "dark-skin",
    "Démon/démone" to "demondemone",
    "Différence d'âge" to "difference-dage",
    "Doujinshi" to "doujinshi",
    "Drame" to "drame",
    "Ecchi" to "ecchi",
    "Échangisme" to "echangisme",
    "Esclave" to "esclave",
    "Espion" to "espion",
    "Exhibitionniste" to "exhibitionniste",
    "Fantaisie" to "fantaisie",
    "Fantastique" to "fantastique",
    "Fétichisme" to "fetichisme",
    "Filming" to "filming",
    "Full color" to "full-color",
    "Furry" to "furry",
    "Gag" to "gag",
    "Gangster" to "gangster",
    "Gender bender" to "gender-bender",
    "Girls love" to "girls-love",
    "Gros seins" to "gros-seins",
    "Group" to "group",
    "Guideverse" to "guideverse",
    "Hardcore" to "hardcore",
    "Harem" to "harem",
    "Hentai" to "hentai",
    "Historique" to "historique",
    "Horreur" to "horreur",
    "Humiliation" to "humiliation",
    "Hypnose" to "hypnose",
    "Ia" to "ia",
    "Immoral" to "immoral",
    "Incest" to "incest",
    "Isekai" to "isekai",
    "Jeux vidéo" to "jeux-video",
    "Josei" to "josei",
    "Lingerie" to "lingerie",
    "Magie" to "magie",
    "Malentendu" to "malentendu",
    "Manga" to "manga",
    "Manga bl" to "manga-bl",
    "Manga h" to "manga-h",
    "Manga josei" to "manga-josei",
    "Manhwa" to "manhwa",
    "Masturbation" to "masturbation",
    "Mature" to "mature",
    "Médical" to "medical",
    "Milf" to "milf",
    "Mini-série" to "mini-serie",
    "Moderne" to "moderne",
    "Muscle" to "muscle",
    "Mystère" to "mystere",
    "Nakadashi" to "nakadashi",
    "Noblesse" to "noblesse",
    "Non-censuré" to "non-censure",
    "Ntr" to "ntr",
    "Obsession" to "obsession",
    "Omégaverse" to "omegaverse",
    "One shot" to "one-shot",
    "Piercing" to "piercing",
    "Plan à 3" to "plan-a-3",
    "Pornhwa" to "pornhwa",
    "Pouvoir" to "pouvoir",
    "Pregnant" to "pregnant",
    "Professeur" to "professeur",
    "Prostitution" to "prostitution",
    "Psychologique" to "psychologique",
    "Rape" to "rape",
    "Réaliste" to "realiste",
    "Réincarnation" to "reincarnation",
    "Romance" to "romance",
    "Schoolgirl uniform" to "schoolgirl-uniform",
    "Science-fiction" to "science-fiction",
    "Seinen" to "seinen",
    "Séquestration" to "sequestration",
    "Sex toys" to "sex-toys",
    "Showbiz" to "showbiz",
    "Smut" to "smut",
    "Sole female" to "sole-female",
    "Spanking" to "spanking",
    "Sports" to "sports",
    "Stockings" to "stockings",
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
    "Voyeurism" to "voyeurism",
    "Webtoon" to "webtoon",
)

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
    "Webtoon" to "webtoon",
)

private val status = listOf(
    "En cours" to "ongoing",
    "Terminée" to "finished",
    "Abandonnée" to "dropped",
    "En pause" to "onhold",
    "À venir" to "upcoming",
)
