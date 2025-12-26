package eu.kanade.tachiyomi.extension.id.medusascans

import eu.kanade.tachiyomi.multisrc.comicaso.Comicaso

class Medusascans : Comicaso(
    "Medusascans",
    "https://medusascans.com",
    "id",
    pageSize = 16,
    excludedGenres = setOf(
        "Crossdressing",
        "Emperors Daughte",
        "Fighting",
        "Genderswap",
        "Girls",
        "Incest",
        "Kids",
        "Philosophical",
        "Reverse Isekai",
        "Sejarah",
        "Superhero",
    ),
)
