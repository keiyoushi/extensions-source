package eu.kanade.tachiyomi.extension.fr.lesporoiniens

import eu.kanade.tachiyomi.multisrc.scanr.ScanR

class LesPoroiniens :
    ScanR(
        name = "Les Poroiniens",
        baseUrl = "https://lesporoiniens.org",
        lang = "fr",
        useHighLowQualityCover = false,
        slugSeparator = "_",
    )
