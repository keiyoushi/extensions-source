package eu.kanade.tachiyomi.extension.tr.limonmanga

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga

class LimonManga :
    UzayManga(
        name = "Limon Manga",
        baseUrl = "https://limonmanga.com",
        lang = "tr",
        versionId = 1,
        cdnUrl = "https://cdn-l.efsaneler2.can.re",
    )
