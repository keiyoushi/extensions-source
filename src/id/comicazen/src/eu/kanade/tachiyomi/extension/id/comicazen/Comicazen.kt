package eu.kanade.tachiyomi.extension.id.comicazen

import eu.kanade.tachiyomi.multisrc.comicaso.Comicaso

class Comicazen : Comicaso(
    "Comicazen",
    "https://comicazen.com",
    "id",
    pageSize = 16,
    excludedGenres = setOf(
        "Adult",
        "Anthology",
        "BDSM",
        "Contest Winning",
        "Fetish",
        "Gore",
        "Hentai",
        "Non Human",
        "Omegaverse",
        "Oneshot",
        "Shoujo Ai",
        "SM BDSM Sub Dom",
        "Space",
        "Sports",
        "Traditional Games",
        "Virtual Reality",
        "Wuxia",
        "Yakuzas",
        "Yaoi(BL)",
    ),
)
