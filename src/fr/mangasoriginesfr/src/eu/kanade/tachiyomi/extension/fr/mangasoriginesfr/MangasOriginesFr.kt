package eu.kanade.tachiyomi.extension.fr.mangasoriginesfr

import eu.kanade.tachiyomi.multisrc.origines.Origines
import keiyoushi.annotation.Source

@Source
abstract class MangasOriginesFr : Origines() {

    override val mangaPath = "oeuvre"

    override val legacyMangaPaths = setOf("catalogues")

    override val origins = listOf(
        "Manhwa" to "manhwa",
        "Manhua" to "manhua",
        "Manga" to "manga",
    )

    override val genres = listOf(
        "Action" to "action",
        "Adventure" to "adventure",
        "Amitié" to "amitie",
        "Amour" to "amour",
        "Art Martiaux" to "art-martiaux",
        "Aventure" to "aventure",
        "BL" to "bl",
        "Boys" to "boys",
        "Combat" to "combat",
        "Comedy" to "comedy",
        "Comédie" to "comedie",
        "Dark Fantasy" to "dark-fantasy",
        "Drama" to "drama",
        "Drame" to "drame",
        "Dystopie" to "dystopie",
        "Démon" to "demon",
        "Ecchi" to "ecchi",
        "Erotique" to "erotique",
        "Fantastique" to "fantastique",
        "Fantasy" to "fantasy",
        "Guerre" to "guerre",
        "Harem" to "harem",
        "Historique" to "historique",
        "Horreur" to "horreur",
        "Isekai" to "isekai",
        "Jeu" to "jeu",
        "Josei" to "josei",
        "Magie" to "magie",
        "Malédiction" to "malediction",
        "Mature" to "mature",
        "Moderne" to "moderne",
        "Mort" to "mort",
        "Murim" to "murim",
        "Musique" to "musique",
        "Mystère" to "mystere",
        "Novel" to "novel",
        "Post-Apo" to "post-apo",
        "Prison" to "prison",
        "Psychologique" to "psychologique",
        "Religion" to "religion",
        "Returner" to "returner",
        "Romance" to "romance",
        "Réincarnation" to "reincarnation",
        "Régression" to "regression",
        "School life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shojo" to "shojo",
        "Shonen" to "shonen",
        "Slice of Life" to "slice-of-life",
        "Société" to "societe",
        "Sorcellerie" to "sorcellerie",
        "Sport" to "sport",
        "Steampunk" to "steampunk",
        "Supernaturel" to "supernaturel",
        "Surnaturel" to "surnaturel",
        "Tragédie" to "tragedie",
        "Vengeance" to "vengeance",
        "Webcomic" to "webcomic",
        "Yuri" to "yuri",
        "École" to "ecole",
    )
}
