package eu.kanade.tachiyomi.extension.en.kaynscans

import eu.kanade.tachiyomi.multisrc.iken.Iken

class KaynScans :
    Iken(
        "Kayn Scans",
        "en",
        "https://kaynscan.org/",
        "https://api.kaynscan.org",
    ) {
    override val versionId = 2
}
