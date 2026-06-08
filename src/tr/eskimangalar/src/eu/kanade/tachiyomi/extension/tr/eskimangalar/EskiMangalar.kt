package eu.kanade.tachiyomi.extension.tr.eskimangalar

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga

class EskiMangalar :
    UzayManga(
        name = "Eski Mangalar",
        baseUrl = "https://eskimangalar.com",
        lang = "tr",
        versionId = 1,
        cdnUrl = "https://cdn-es.efsaneler2.can.re",
    )
