package eu.kanade.tachiyomi.extension.fr.hentaiorigines

import eu.kanade.tachiyomi.multisrc.origines.Origines
import keiyoushi.annotation.Source

@Source
abstract class HentaiOrigines : Origines() {

    override val mangaPath = "manga"

    override val origins = listOf(
        "Doujinshi" to "doujinshi",
        "Pornhwa" to "pornhwa",
        "Pornhua" to "pornhua",
        "Hentai" to "hentai",
    )

    override val genres = listOf(
        "Action" to "action",
        "Alien" to "alien",
        "Alpha" to "alpha",
        "Amitié" to "amitie",
        "Art martiaux" to "art-martiaux",
        "Aventure" to "aventure",
        "Belle-mère" to "belle-mere",
        "Boy's Love" to "yaoi",
        "Campus" to "campus",
        "Comédie" to "comedie",
        "Domination" to "domination",
        "Drame" to "drame",
        "Démon" to "demon",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Futanari" to "futanari",
        "Furry" to "furry",
        "Fétichisme" to "fetichisme",
        "Gallerie" to "gallerie",
        "Gangster" to "gangster",
        "Gofast" to "gofast",
        "Gore" to "gore",
        "Guideverse" to "guideverse",
        "Hardcore" to "hardcore",
        "Harem" to "harem",
        "Historique" to "historique",
        "Horreur" to "horreur",
        "Humiliation" to "humiliation",
        "Inceste" to "inceste",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Loli" to "loli",
        "Love" to "love",
        "Magie" to "magie",
        "Mature" to "mature",
        "Milf" to "milf",
        "Mini-série" to "mini-serie",
        "Monsters girls" to "monsters-girls",
        "Ntr" to "ntr",
        "Office" to "office",
        "Omégaverse" to "omegaverse",
        "Oneshot" to "oneshot",
        "Parodie" to "parodie",
        "Professeur" to "professeur",
        "Psychologie" to "psychologie",
        "Rape" to "rape",
        "Romance" to "romance",
        "Réincarnation" to "reincarnation",
        "School life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Shonen-ai" to "shonen-ai",
        "Slice of life" to "slice-of-life",
        "Smut" to "smut",
        "Soft" to "soft",
        "Sport" to "sport",
        "Surnaturel" to "surnaturel",
        "Tomgirl" to "tomgirl",
        "Tragédie" to "tragedie",
        "Triangle amoureux" to "triangle-amoureux",
        "Uncensored" to "uncensored",
        "Yuri" to "yuri",
    )
}
