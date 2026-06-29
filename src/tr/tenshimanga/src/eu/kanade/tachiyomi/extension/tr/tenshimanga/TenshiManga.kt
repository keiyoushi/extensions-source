package eu.kanade.tachiyomi.extension.tr.tenshimanga

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga

class TenshiManga :
    UzayManga(
        name = "Tenshi Manga",
        baseUrl = "https://tenshimanga.com",
        lang = "tr",
        versionId = 2,
        cdnUrl = "https://cdn-t.efsaneler2.can.re",
    )
