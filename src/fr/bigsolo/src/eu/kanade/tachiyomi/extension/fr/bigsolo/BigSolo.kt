package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.multisrc.scanr.ScanR

class BigSolo : ScanR(
    name = "Big Solo",
    baseUrl = "https://bigsolo.org",
    lang = "fr",
    useHighLowQualityCover = true,
)
